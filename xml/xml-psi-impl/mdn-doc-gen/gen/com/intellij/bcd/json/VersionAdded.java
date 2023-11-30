
package com.intellij.bcd.json;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = VersionAdded.MyDeserializer.class)
public class VersionAdded {

    /**
     * Type: {@code String | Boolean}
     * 
     */
    private Object value;

    /**
     * Type: {@code String | Boolean}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code String | Boolean}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<VersionAdded>
    {


        @Override
        public VersionAdded deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            VersionAdded result = new VersionAdded();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                result.value = parser.readValueAs(String.class);
            } else {
                if ((token == JsonToken.VALUE_TRUE)||(token == JsonToken.VALUE_FALSE)) {
                    result.value = parser.readValueAs(Boolean.class);
                } else {
                    deserializationContext.handleUnexpectedToken(Object.class, parser);
                }
            }
            return result;
        }

    }

}
