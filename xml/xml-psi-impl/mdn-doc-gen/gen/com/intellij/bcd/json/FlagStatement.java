
package com.intellij.bcd.json;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "type",
    "name",
    "value_to_set"
})
public class FlagStatement {

    /**
     * An enum that indicates the flag type.
     * (Required)
     * 
     */
    @JsonProperty("type")
    @JsonPropertyDescription("An enum that indicates the flag type.")
    private FlagStatement.Type type;
    /**
     * A string giving the name of the flag or preference that must be configured.
     * (Required)
     * 
     */
    @JsonProperty("name")
    @JsonPropertyDescription("A string giving the name of the flag or preference that must be configured.")
    private String name;
    /**
     * A string giving the value which the specified flag must be set to for this feature to work.
     * 
     */
    @JsonProperty("value_to_set")
    @JsonPropertyDescription("A string giving the value which the specified flag must be set to for this feature to work.")
    private String valueToSet;

    /**
     * An enum that indicates the flag type.
     * (Required)
     * 
     */
    @JsonProperty("type")
    public FlagStatement.Type getType() {
        return type;
    }

    /**
     * An enum that indicates the flag type.
     * (Required)
     * 
     */
    @JsonProperty("type")
    public void setType(FlagStatement.Type type) {
        this.type = type;
    }

    /**
     * A string giving the name of the flag or preference that must be configured.
     * (Required)
     * 
     */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /**
     * A string giving the name of the flag or preference that must be configured.
     * (Required)
     * 
     */
    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * A string giving the value which the specified flag must be set to for this feature to work.
     * 
     */
    @JsonProperty("value_to_set")
    public String getValueToSet() {
        return valueToSet;
    }

    /**
     * A string giving the value which the specified flag must be set to for this feature to work.
     * 
     */
    @JsonProperty("value_to_set")
    public void setValueToSet(String valueToSet) {
        this.valueToSet = valueToSet;
    }


    /**
     * An enum that indicates the flag type.
     * 
     */
    public enum Type {

        PREFERENCE("preference"),
        RUNTIME_FLAG("runtime_flag");
        private final String value;
        private final static Map<String, FlagStatement.Type> CONSTANTS = new HashMap<String, FlagStatement.Type>();

        static {
            for (FlagStatement.Type c: values()) {
                CONSTANTS.put(c.value, c);
            }
        }

        private Type(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return this.value;
        }

        @JsonValue
        public String value() {
            return this.value;
        }

        @JsonCreator
        public static FlagStatement.Type fromValue(String value) {
            FlagStatement.Type constant = CONSTANTS.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }

    }

}
