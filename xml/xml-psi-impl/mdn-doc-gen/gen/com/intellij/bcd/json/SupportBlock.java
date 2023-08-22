
package com.intellij.bcd.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({

})
public class SupportBlock {

    @JsonIgnore
    private Map<String, SupportStatement> additionalProperties = new HashMap<String, SupportStatement>();

    @JsonAnyGetter
    public Map<String, SupportStatement> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, SupportStatement value) {
        this.additionalProperties.put(name, value);
    }

}
