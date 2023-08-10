
package com.intellij.bcd.json;

import java.io.IOException;
import java.util.List;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = SupportStatement.MyDeserializer.class)
public class SupportStatement {

    /**
     * Type: {@code SimpleSupportStatement | List<SimpleSupportStatement> | String}
     * 
     */
    private Object value;

    /**
     * Type: {@code SimpleSupportStatement | List<SimpleSupportStatement> | String}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code SimpleSupportStatement | List<SimpleSupportStatement> | String}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends JsonDeserializer<SupportStatement>
    {


        @Override
        public SupportStatement deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            SupportStatement result = new SupportStatement();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_OBJECT) {
                result.value = parser.readValueAs(SimpleSupportStatement.class);
            } else {
                if (token == JsonToken.START_ARRAY) {
                    result.value = parser.getCodec().readValue(parser, deserializationContext.getTypeFactory().constructParametricType(List.class, SimpleSupportStatement.class));
                } else {
                    if (token == JsonToken.VALUE_STRING) {
                        result.value = parser.readValueAs(String.class);
                    } else {
                        deserializationContext.handleUnexpectedToken(Object.class, parser);
                    }
                }
            }
            return result;
        }

    }

}
