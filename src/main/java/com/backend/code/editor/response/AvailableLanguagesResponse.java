package com.backend.code.editor.response;

import com.backend.code.editor.language.Language;

import java.util.List;

public record AvailableLanguagesResponse(List<Language> availableLanguages) {
}
