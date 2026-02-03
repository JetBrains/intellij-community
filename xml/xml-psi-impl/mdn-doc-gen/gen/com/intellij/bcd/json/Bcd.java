
package com.intellij.bcd.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * CompatDataFile
 * <p>
 * 
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "__compat",
    "__meta",
    "browsers",
    "webextensions"
})
public class Bcd {

    @JsonProperty("__compat")
    private CompatStatement compat;
    @JsonProperty("__meta")
    private Meta meta;
    @JsonProperty("browsers")
    private Browsers browsers;
    @JsonProperty("webextensions")
    private Webextensions webextensions;
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

    @JsonProperty("__meta")
    public Meta getMeta() {
        return meta;
    }

    @JsonProperty("__meta")
    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    @JsonProperty("browsers")
    public Browsers getBrowsers() {
        return browsers;
    }

    @JsonProperty("browsers")
    public void setBrowsers(Browsers browsers) {
        this.browsers = browsers;
    }

    @JsonProperty("webextensions")
    public Webextensions getWebextensions() {
        return webextensions;
    }

    @JsonProperty("webextensions")
    public void setWebextensions(Webextensions webextensions) {
        this.webextensions = webextensions;
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
