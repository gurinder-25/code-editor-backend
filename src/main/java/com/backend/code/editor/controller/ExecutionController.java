package com.backend.code.editor.controller;

import com.backend.code.editor.request.ExecuteRequest;
import com.backend.code.editor.response.ExecuteResponse;
import com.backend.code.editor.service.CodeExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ExecutionController {

    private final CodeExecutionService codeExecutionService;

    public ExecutionController(CodeExecutionService codeExecutionService) {
        this.codeExecutionService = codeExecutionService;
    }

    @PostMapping("execute")
    public ResponseEntity<ExecuteResponse> execute(@Valid @RequestBody ExecuteRequest executeRequest) {
        return ResponseEntity.ok(codeExecutionService.execute(executeRequest));
    }
}
