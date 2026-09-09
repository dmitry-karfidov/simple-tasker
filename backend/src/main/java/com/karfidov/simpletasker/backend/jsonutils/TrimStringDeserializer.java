package com.karfidov.simpletasker.backend.jsonutils;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public class TrimStringDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext context) {
        String value = p.getValueAsString();
        return value == null ? null : value.trim();
    }
}
