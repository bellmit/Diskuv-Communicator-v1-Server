package com.diskuv.communicator.configurator;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.dropwizard.jackson.Jackson;

import java.lang.reflect.Field;

import static org.apache.commons.lang3.reflect.FieldUtils.getDeclaredField;
import static org.apache.commons.lang3.reflect.FieldUtils.writeField;

public final class ConfigurationUtils {
    private static final boolean FORCE_ACCESS = true;

    private ConfigurationUtils() {
    }

    public static void setField(Object target, String fieldName, Object value) throws IllegalAccessException {
        assert target.getClass() != null;
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            Field field = getDeclaredField(clazz, fieldName, FORCE_ACCESS);
            if (field != null) {
                writeField(field, target, value, FORCE_ACCESS);
                return;
            }
            clazz = clazz.getSuperclass();
        }
        throw new IllegalArgumentException(String.format("Cannot locate declared field %s.%s",
                target.getClass().getName(), fieldName));
    }

    public static ObjectMapper mapperForWriting() {
        ObjectMapper mapper = Jackson.newObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        return mapper;
    }
}
