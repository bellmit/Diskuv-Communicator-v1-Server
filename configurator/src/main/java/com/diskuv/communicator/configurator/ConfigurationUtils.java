package com.diskuv.communicator.configurator;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import org.whispersystems.textsecuregcm.WhisperServerConfiguration;

import javax.validation.ValidatorFactory;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

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

    public static ConfigurationFactory<WhisperServerConfiguration> createConfigurationBuilder() {
        DefaultConfigurationFactoryFactory<WhisperServerConfiguration> configurationFactoryFactory = new DefaultConfigurationFactoryFactory<>();
        ValidatorFactory validatorFactory = Validators.newValidatorFactory();
        ObjectMapper objectMapper = Jackson.newObjectMapper();
        return configurationFactoryFactory.create(WhisperServerConfiguration.class, validatorFactory.getValidator(),
                objectMapper, "dw");
    }

    public static String convertToYaml(WhisperServerConfiguration configuration) throws JsonProcessingException {
        // We'll use a two-step conversion strategy of
        // https://github.com/FasterXML/jackson-databind#tutorial-fancier-stuff-conversions
        // so we can do necessary tweaks to the YAML.

        // First, convert WhisperServerConfiguration to a Map (aka a JSON object).
        ObjectMapper mapper1 = Jackson.newObjectMapper();
        mapper1.setVisibility(mapper1.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        Map jsonObject = mapper1.convertValue(configuration, Map.class);

        // Now do tweaks.
        // The following fields do not serialize well or were inadvertently picked up by Jackson ...
        // * server.requestLog.appenders.[0]:
        //   Missing type id when trying to resolve subtype of [simple type, class io.dropwizard.logging.AppenderFactory<ch.qos.logback.access.spi.IAccessEvent>]: missing type id property 'type' (for POJO property 'appenders')
        // * gcpAttachments.pathPrefixValid:
        //   Unrecognized field at: gcpAttachments.pathPrefixValid
        // * apn.sandboxEnabled:
        //   Unrecognized field at: apn.sandboxEnabled
        // * server.applicationConnectors.[0].validKeyStorePath
        //   Unrecognized field at: server.applicationConnectors.[0].validKeyStorePath
        // * server.applicationConnectors.[0].validKeyStorePassword
        //   Unrecognized field at: server.applicationConnectors.[0].validKeyStorePassword
        Map server = (Map) jsonObject.get("server");
        server.remove("requestLog");
        Map gcpAttachments = (Map) jsonObject.get("gcpAttachments");
        gcpAttachments.remove("pathPrefixValid");
        Map apn = (Map) jsonObject.get("apn");
        apn.remove("sandboxEnabled");
        List applicationConnectors = (List) server.get("applicationConnectors");
        if (applicationConnectors != null) {
            for (Object o : applicationConnectors) {
                Map applicationConnector = (Map) o;
                applicationConnector.remove("validKeyStorePath");
                applicationConnector.remove("validKeyStorePassword");
            }
        }

        // Second, convert Map to YAML
        ObjectMapper mapper2 = Jackson.newObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        return mapper2.writeValueAsString(jsonObject);
    }
}
