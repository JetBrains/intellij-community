
package com.intellij.bcd.json;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "version_added",
    "version_removed",
    "prefix",
    "alternative_name",
    "flags",
    "impl_url",
    "partial_implementation",
    "notes"
})
public class SimpleSupportStatement {

    /**
     * A string (indicating which browser version added this feature), the value true (indicating support added in an unknown version), the value false (indicating the feature is not supported), or the value null (indicating support is unknown).
     * (Required)
     * 
     */
    @JsonProperty("version_added")
    @JsonPropertyDescription("A string (indicating which browser version added this feature), the value true (indicating support added in an unknown version), the value false (indicating the feature is not supported), or the value null (indicating support is unknown).")
    private VersionAdded versionAdded;
    /**
     * A string, indicating which browser version removed this feature, or the value true, indicating that the feature was removed in an unknown version.
     * 
     */
    @JsonProperty("version_removed")
    @JsonPropertyDescription("A string, indicating which browser version removed this feature, or the value true, indicating that the feature was removed in an unknown version.")
    private VersionRemoved versionRemoved;
    /**
     * A prefix to add to the sub-feature name (defaults to empty string). If applicable, leading and trailing '-' must be included.
     * 
     */
    @JsonProperty("prefix")
    @JsonPropertyDescription("A prefix to add to the sub-feature name (defaults to empty string). If applicable, leading and trailing '-' must be included.")
    private String prefix;
    /**
     * An alternative name for the feature, for cases where a feature is implemented under an entirely different name and not just prefixed.
     * 
     */
    @JsonProperty("alternative_name")
    @JsonPropertyDescription("An alternative name for the feature, for cases where a feature is implemented under an entirely different name and not just prefixed.")
    private String alternativeName;
    /**
     * An optional array of objects describing flags that must be configured for this browser to support this feature.
     * 
     */
    @JsonProperty("flags")
    @JsonPropertyDescription("An optional array of objects describing flags that must be configured for this browser to support this feature.")
    private List<FlagStatement> flags = new ArrayList<FlagStatement>();
    /**
     * An optional changeset/commit URL for the revision which implemented the feature in the source code, or the URL to the bug tracking the implementation, for the associated browser.
     * 
     */
    @JsonProperty("impl_url")
    @JsonPropertyDescription("An optional changeset/commit URL for the revision which implemented the feature in the source code, or the URL to the bug tracking the implementation, for the associated browser.")
    private ImplUrl implUrl;
    /**
     * A boolean value indicating whether or not the implementation of the sub-feature deviates from the specification in a way that may cause compatibility problems. It defaults to false (no interoperability problems expected). If set to true, it is recommended that you add a note explaining how it diverges from the standard (such as that it implements an old version of the standard, for example).
     * 
     */
    @JsonProperty("partial_implementation")
    @JsonPropertyDescription("A boolean value indicating whether or not the implementation of the sub-feature deviates from the specification in a way that may cause compatibility problems. It defaults to false (no interoperability problems expected). If set to true, it is recommended that you add a note explaining how it diverges from the standard (such as that it implements an old version of the standard, for example).")
    private Object partialImplementation;
    /**
     * A string or array of strings containing additional information.
     * 
     */
    @JsonProperty("notes")
    @JsonPropertyDescription("A string or array of strings containing additional information.")
    private Notes notes;

    /**
     * A string (indicating which browser version added this feature), the value true (indicating support added in an unknown version), the value false (indicating the feature is not supported), or the value null (indicating support is unknown).
     * (Required)
     * 
     */
    @JsonProperty("version_added")
    public VersionAdded getVersionAdded() {
        return versionAdded;
    }

    /**
     * A string (indicating which browser version added this feature), the value true (indicating support added in an unknown version), the value false (indicating the feature is not supported), or the value null (indicating support is unknown).
     * (Required)
     * 
     */
    @JsonProperty("version_added")
    public void setVersionAdded(VersionAdded versionAdded) {
        this.versionAdded = versionAdded;
    }

    /**
     * A string, indicating which browser version removed this feature, or the value true, indicating that the feature was removed in an unknown version.
     * 
     */
    @JsonProperty("version_removed")
    public VersionRemoved getVersionRemoved() {
        return versionRemoved;
    }

    /**
     * A string, indicating which browser version removed this feature, or the value true, indicating that the feature was removed in an unknown version.
     * 
     */
    @JsonProperty("version_removed")
    public void setVersionRemoved(VersionRemoved versionRemoved) {
        this.versionRemoved = versionRemoved;
    }

    /**
     * A prefix to add to the sub-feature name (defaults to empty string). If applicable, leading and trailing '-' must be included.
     * 
     */
    @JsonProperty("prefix")
    public String getPrefix() {
        return prefix;
    }

    /**
     * A prefix to add to the sub-feature name (defaults to empty string). If applicable, leading and trailing '-' must be included.
     * 
     */
    @JsonProperty("prefix")
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * An alternative name for the feature, for cases where a feature is implemented under an entirely different name and not just prefixed.
     * 
     */
    @JsonProperty("alternative_name")
    public String getAlternativeName() {
        return alternativeName;
    }

    /**
     * An alternative name for the feature, for cases where a feature is implemented under an entirely different name and not just prefixed.
     * 
     */
    @JsonProperty("alternative_name")
    public void setAlternativeName(String alternativeName) {
        this.alternativeName = alternativeName;
    }

    /**
     * An optional array of objects describing flags that must be configured for this browser to support this feature.
     * 
     */
    @JsonProperty("flags")
    public List<FlagStatement> getFlags() {
        return flags;
    }

    /**
     * An optional array of objects describing flags that must be configured for this browser to support this feature.
     * 
     */
    @JsonProperty("flags")
    public void setFlags(List<FlagStatement> flags) {
        this.flags = flags;
    }

    /**
     * An optional changeset/commit URL for the revision which implemented the feature in the source code, or the URL to the bug tracking the implementation, for the associated browser.
     * 
     */
    @JsonProperty("impl_url")
    public ImplUrl getImplUrl() {
        return implUrl;
    }

    /**
     * An optional changeset/commit URL for the revision which implemented the feature in the source code, or the URL to the bug tracking the implementation, for the associated browser.
     * 
     */
    @JsonProperty("impl_url")
    public void setImplUrl(ImplUrl implUrl) {
        this.implUrl = implUrl;
    }

    /**
     * A boolean value indicating whether or not the implementation of the sub-feature deviates from the specification in a way that may cause compatibility problems. It defaults to false (no interoperability problems expected). If set to true, it is recommended that you add a note explaining how it diverges from the standard (such as that it implements an old version of the standard, for example).
     * 
     */
    @JsonProperty("partial_implementation")
    public Object getPartialImplementation() {
        return partialImplementation;
    }

    /**
     * A boolean value indicating whether or not the implementation of the sub-feature deviates from the specification in a way that may cause compatibility problems. It defaults to false (no interoperability problems expected). If set to true, it is recommended that you add a note explaining how it diverges from the standard (such as that it implements an old version of the standard, for example).
     * 
     */
    @JsonProperty("partial_implementation")
    public void setPartialImplementation(Object partialImplementation) {
        this.partialImplementation = partialImplementation;
    }

    /**
     * A string or array of strings containing additional information.
     * 
     */
    @JsonProperty("notes")
    public Notes getNotes() {
        return notes;
    }

    /**
     * A string or array of strings containing additional information.
     * 
     */
    @JsonProperty("notes")
    public void setNotes(Notes notes) {
        this.notes = notes;
    }

}
