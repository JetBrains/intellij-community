
package com.intellij.bcd.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "experimental",
    "standard_track",
    "deprecated"
})
public class StatusBlock {

    /**
     * A boolean value that indicates whether this functionality is intended to be an addition to the Web platform. Set to false, it means the functionality is mature, and no significant incompatible changes are expected in the future.
     * (Required)
     * 
     */
    @JsonProperty("experimental")
    @JsonPropertyDescription("A boolean value that indicates whether this functionality is intended to be an addition to the Web platform. Set to false, it means the functionality is mature, and no significant incompatible changes are expected in the future.")
    private Boolean experimental;
    /**
     * A boolean value indicating whether the feature is part of an active specification or specification process.
     * (Required)
     * 
     */
    @JsonProperty("standard_track")
    @JsonPropertyDescription("A boolean value indicating whether the feature is part of an active specification or specification process.")
    private Boolean standardTrack;
    /**
     * A boolean value that indicates whether the feature is no longer recommended. It might be removed in the future or might only be kept for compatibility purposes. Avoid using this functionality.
     * (Required)
     * 
     */
    @JsonProperty("deprecated")
    @JsonPropertyDescription("A boolean value that indicates whether the feature is no longer recommended. It might be removed in the future or might only be kept for compatibility purposes. Avoid using this functionality.")
    private Boolean deprecated;

    /**
     * A boolean value that indicates whether this functionality is intended to be an addition to the Web platform. Set to false, it means the functionality is mature, and no significant incompatible changes are expected in the future.
     * (Required)
     * 
     */
    @JsonProperty("experimental")
    public Boolean getExperimental() {
        return experimental;
    }

    /**
     * A boolean value that indicates whether this functionality is intended to be an addition to the Web platform. Set to false, it means the functionality is mature, and no significant incompatible changes are expected in the future.
     * (Required)
     * 
     */
    @JsonProperty("experimental")
    public void setExperimental(Boolean experimental) {
        this.experimental = experimental;
    }

    /**
     * A boolean value indicating whether the feature is part of an active specification or specification process.
     * (Required)
     * 
     */
    @JsonProperty("standard_track")
    public Boolean getStandardTrack() {
        return standardTrack;
    }

    /**
     * A boolean value indicating whether the feature is part of an active specification or specification process.
     * (Required)
     * 
     */
    @JsonProperty("standard_track")
    public void setStandardTrack(Boolean standardTrack) {
        this.standardTrack = standardTrack;
    }

    /**
     * A boolean value that indicates whether the feature is no longer recommended. It might be removed in the future or might only be kept for compatibility purposes. Avoid using this functionality.
     * (Required)
     * 
     */
    @JsonProperty("deprecated")
    public Boolean getDeprecated() {
        return deprecated;
    }

    /**
     * A boolean value that indicates whether the feature is no longer recommended. It might be removed in the future or might only be kept for compatibility purposes. Avoid using this functionality.
     * (Required)
     * 
     */
    @JsonProperty("deprecated")
    public void setDeprecated(Boolean deprecated) {
        this.deprecated = deprecated;
    }

}
