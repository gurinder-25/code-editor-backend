package com.backend.code.editor.language;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Component
public class CodeSnippetRegistry {

    private final Map<Language, String> snippets;

    public CodeSnippetRegistry(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("snippets/code-snippets.json").getInputStream()) {
            this.snippets = objectMapper.readValue(in, new TypeReference<Map<Language, String>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load snippets/code-snippets.json", e);
        }
    }

    public String get(Language language) {
        return snippets.get(language);
    }
}
