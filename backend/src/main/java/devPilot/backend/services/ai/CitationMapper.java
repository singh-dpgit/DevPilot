package devPilot.backend.services.ai;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import devPilot.backend.dto.CitationDto;
import lombok.RequiredArgsConstructor;

// Maps documents to citations
@Component
@RequiredArgsConstructor
public class CitationMapper {

    // JSON converter
    private final JsonMapper jsonMapper;

    // Extracts citation details from document
    public CitationDto fromDocument(Document document) {
        var meta = document.getMetadata();
        return new CitationDto(
                stringVal(meta.get("filePath")),
                intVal(meta.get("startLine")),
                intVal(meta.get("endLine")),
                stringVal(meta.get("language")));
    }

    // Converts citations to JSON
    public String toJson(List<CitationDto> citations) {
        try {
            return jsonMapper.writeValueAsString(citations);
        } catch (JacksonException e) {
            return "[]";
        }
    }

    // Converts JSON back to citations
    public List<CitationDto> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(json, new TypeReference<>() {});
        } catch (JacksonException e) {
            return List.of();
        }
    }

    // Converts metadata to String
    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // Converts metadata to Integer
    private static Integer intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}