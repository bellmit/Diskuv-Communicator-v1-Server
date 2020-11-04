package org.whispersystems.textsecuregcm.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotEmpty;

public class MicrometerConfiguration {

    @JsonProperty
    private String uri;

    public String getUri() {
        return uri;
    }
}
