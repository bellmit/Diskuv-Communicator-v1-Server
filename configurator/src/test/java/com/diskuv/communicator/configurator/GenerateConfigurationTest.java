package com.diskuv.communicator.configurator;

import org.junit.Test;

import java.security.InvalidKeyException;
import java.util.Random;

import static org.junit.Assert.*;

public class GenerateConfigurationTest {

    @Test
    public void givenNoOptions__whenCreateWhisperServerConfiguration__thenValidWhisperServerConfiguration() throws InvalidKeyException, IllegalAccessException {
        Random random = new Random(77);
        GenerateConfiguration generateConfiguration = new GenerateConfiguration(random);
        generateConfiguration.createWhisperServerConfiguration();
    }
}