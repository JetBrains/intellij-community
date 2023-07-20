
package com.intellij.bcd.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "__compat"
})
public class Identifier {

    @JsonProperty("__compat")
    private CompatStatement compat;
    @JsonIgnore
    private Map<String, Identifier> additionalProperties = new HashMap<String, Identifier>();

    @JsonProperty("__compat")
    public CompatStatement getCompat() {
        return compat;
    }

    @JsonProperty("__compat")
    public void setCompat(CompatStatement compat) {
        this.compat = compat;
    }

    @JsonAnyGetter
    public Map<String, Identifier> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Identifier value) {
        this.additionalProperties.put(name, value);
    }

}
