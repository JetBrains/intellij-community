package com.test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.test.BuilderJacksonized.*;

@Value
@Builder(toBuilder = true)
@Jacksonized
@JsonPropertyOrder({
        ID_PROP,
        SERVICE_PROP,
        COMPONENT_PROP
})
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
public class BuilderJacksonized {
    public static final String ID_PROP = "id";
    public static final String SERVICE_PROP = "service";
    public static final String COMPONENT_PROP = "component";

    @JsonProperty(value = ID_PROP, required = true)
    @NonNull String id;

    @JsonProperty(value = SERVICE_PROP, required = true)
    @NonNull String service;

    @JsonProperty(value = COMPONENT_PROP, required = true)
    @NonNull String component;
}
