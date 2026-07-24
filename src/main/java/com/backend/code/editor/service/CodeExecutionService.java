package com.backend.code.editor.service;

import com.backend.code.editor.exception.ExecutionFailedException;
import com.backend.code.editor.language.LanguageConfig;
import com.backend.code.editor.language.LanguageRegistry;
import com.backend.code.editor.request.ExecuteRequest;
import com.backend.code.editor.response.ExecuteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class CodeExecutionService {

    private static final long TIMEOUT_SECONDS = 10;
    private static final int MAX_OUTPUT_BYTES = 1_048_576;
    private static final int MAX_CONCURRENT_EXECUTIONS = 2;

    private final LanguageRegistry languageRegistry;
    private final String workDirBase;
    private final Semaphore executionSlots = new Semaphore(MAX_CONCURRENT_EXECUTIONS);

    public CodeExecutionService(LanguageRegistry languageRegistry,
                                 @Value("${executor.work-dir:}") String workDirBase) {
        this.languageRegistry = languageRegistry;
        this.workDirBase = workDirBase;
    }

    public ExecuteResponse execute(ExecuteRequest request) {
        LanguageConfig config = languageRegistry.get(request.language());
        Path workDir = createWorkDir(config, request.code());
        long start = System.currentTimeMillis();

        try {
            executionSlots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanup(workDir);
            throw new ExecutionFailedException("Execution was interrupted while waiting for a free slot", e);
        }

        try {
            Process process = startContainer(config, workDir);
            writeStdin(process, request.stdin());

            CompletableFuture<String> stdout = readStream(process.getInputStream());
            CompletableFuture<String> stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;

            if (!finished) {
                process.destroyForcibly();
                return ExecuteResponse.timeout(
                        stdout.completeOnTimeout("", 1, TimeUnit.SECONDS).join(),
                        "Execution timed out after " + TIMEOUT_SECONDS + " seconds",
                        elapsed);
            }
            return ExecuteResponse.success(stdout.join(), stderr.join(), process.exitValue(), elapsed);
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to start execution container. Is Docker running?", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionFailedException("Execution was interrupted", e);
        } finally {
            executionSlots.release();
            cleanup(workDir);
        }
    }

    private Path createWorkDir(LanguageConfig config, String code) {
        try {
            Path dir;
            if (workDirBase == null || workDirBase.isBlank()) {
                dir = Files.createTempDirectory("code-exec-");
            } else {
                Path base = Files.createDirectories(Path.of(workDirBase));
                dir = Files.createTempDirectory(base, "code-exec-");
            }
            Files.writeString(dir.resolve(config.fileName()), code, StandardCharsets.UTF_8);
            return dir;
        } catch (IOException e) {
            throw new ExecutionFailedException("Failed to write code to a temporary directory", e);
        }
    }

    private Process startContainer(LanguageConfig config, Path workDir) throws IOException {
        List<String> command = List.of(
                "docker", "run",
                "--rm",
                "-i",
                "--network", "none",
                "--memory", "256m",
                "--cpus", "1.0",
                "--pids-limit", "128",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "-v", workDir.toAbsolutePath() + ":/code:ro",
                config.image(),
                "sh", "-c", config.runCommand()
        );
        return new ProcessBuilder(command).start();
    }

    private void writeStdin(Process process, String stdin) {
        try (OutputStream in = process.getOutputStream()) {
            if (stdin != null && !stdin.isEmpty()) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // Container may exit before reading stdin — not an error.
        }
    }

    private CompletableFuture<String> readStream(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                byte[] bytes = stream.readNBytes(MAX_OUTPUT_BYTES);
                String output = new String(bytes, StandardCharsets.UTF_8);
                if (stream.read() != -1) {
                    output += "\n... [output truncated at " + MAX_OUTPUT_BYTES + " bytes]";
                }
                return output;
            } catch (IOException e) {
                return "";
            }
        });
    }

    private void cleanup(Path workDir) {
        try {
            FileSystemUtils.deleteRecursively(workDir);
        } catch (IOException e) {
            // Best-effort cleanup; temp dir will be reaped by the OS eventually.
        }
    }
}
