package com.genkey.healthlogger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genkey.healthlogger.model.HealthEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Json {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}

    public static ClientSpec loadSpec(Path path) throws IOException {
        try (var in = Files.newInputStream(path)) {
            return MAPPER.readValue(in, ClientSpec.class);
        }
    }

    
      public static HealthEvent parseEvent(String jsonLine) throws IOException {
    Map<String, Object> fields =
            MAPPER.readValue(jsonLine, new TypeReference<>() {});

    if (fields == null) {
        throw new IOException("JSON event must be an object");
    }

    return new HealthEvent(fields);
}


    public static String toJson(Object value) throws IOException {
        return MAPPER.writeValueAsString(value);
    }

    public static List<String> validate(HealthEvent event, ClientSpec spec) {
        List<String> issues = new ArrayList<>();
        Map<String, Object> fields = event.fields();
        if (spec.requiredFields() != null) {
            for (String key : spec.requiredFields()) {
                if (!fields.containsKey(key) || fields.get(key) == null) {
                    issues.add("missing required field: " + key);
                }
            }
        }
        String status = event.status();
        if (status != null && spec.allowedStatuses() != null && !spec.allowedStatuses().contains(status)) {
            issues.add("status not allowed: " + status);
        }
        return issues;
    }
}
