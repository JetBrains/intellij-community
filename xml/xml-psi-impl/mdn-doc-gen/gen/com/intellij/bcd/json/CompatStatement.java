
package com.intellij.bcd.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "description",
    "mdn_url",
    "spec_url",
    "source_file",
    "support",
    "status"
})
public class CompatStatement {

    /**
     * A string containing a human-readable description of the feature.
     * 
     */
    @JsonProperty("description")
    @JsonPropertyDescription("A string containing a human-readable description of the feature.")
    private String description;
    /**
     * A URL that points to an MDN reference page documenting the feature. The URL should be language-agnostic.
     * 
     */
    @JsonProperty("mdn_url")
    @JsonPropertyDescription("A URL that points to an MDN reference page documenting the feature. The URL should be language-agnostic.")
    private String mdnUrl;
    /**
     * An optional URL or array of URLs, each of which is for a specific part of a specification in which this feature is defined. Each URL must contain a fragment identifier.
     * 
     */
    @JsonProperty("spec_url")
    @JsonPropertyDescription("An optional URL or array of URLs, each of which is for a specific part of a specification in which this feature is defined. Each URL must contain a fragment identifier.")
    private SpecUrl specUrl;
    /**
     * The path to the file that defines this feature in browser-compat-data, relative to the repository root. Useful for guiding potential contributors towards the correct file to edit. This is automatically generated at build time and should never manually be specified.
     * 
     */
    @JsonProperty("source_file")
    @JsonPropertyDescription("The path to the file that defines this feature in browser-compat-data, relative to the repository root. Useful for guiding potential contributors towards the correct file to edit. This is automatically generated at build time and should never manually be specified.")
    private String sourceFile;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("support")
    private SupportBlock support;
    @JsonProperty("status")
    private StatusBlock status;

    /**
     * A string containing a human-readable description of the feature.
     * 
     */
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    /**
     * A string containing a human-readable description of the feature.
     * 
     */
    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * A URL that points to an MDN reference page documenting the feature. The URL should be language-agnostic.
     * 
     */
    @JsonProperty("mdn_url")
    public String getMdnUrl() {
        return mdnUrl;
    }

    /**
     * A URL that points to an MDN reference page documenting the feature. The URL should be language-agnostic.
     * 
     */
    @JsonProperty("mdn_url")
    public void setMdnUrl(String mdnUrl) {
        this.mdnUrl = mdnUrl;
    }

    /**
     * An optional URL or array of URLs, each of which is for a specific part of a specification in which this feature is defined. Each URL must contain a fragment identifier.
     * 
     */
    @JsonProperty("spec_url")
    public SpecUrl getSpecUrl() {
        return specUrl;
    }

    /**
     * An optional URL or array of URLs, each of which is for a specific part of a specification in which this feature is defined. Each URL must contain a fragment identifier.
     * 
     */
    @JsonProperty("spec_url")
    public void setSpecUrl(SpecUrl specUrl) {
        this.specUrl = specUrl;
    }

    /**
     * The path to the file that defines this feature in browser-compat-data, relative to the repository root. Useful for guiding potential contributors towards the correct file to edit. This is automatically generated at build time and should never manually be specified.
     * 
     */
    @JsonProperty("source_file")
    public String getSourceFile() {
        return sourceFile;
    }

    /**
     * The path to the file that defines this feature in browser-compat-data, relative to the repository root. Useful for guiding potential contributors towards the correct file to edit. This is automatically generated at build time and should never manually be specified.
     * 
     */
    @JsonProperty("source_file")
    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("support")
    public SupportBlock getSupport() {
        return support;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("support")
    public void setSupport(SupportBlock support) {
        this.support = support;
    }

    @JsonProperty("status")
    public StatusBlock getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(StatusBlock status) {
        this.status = status;
    }

}
