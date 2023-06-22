
package com.intellij.bcd.json;

import java.io.IOException;
import java.util.ArrayList;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = SpecUrl.MyDeserializer.class)
public class SpecUrl
    extends ArrayList<String>
{


    public static class MyDeserializer
        extends JsonDeserializer<SpecUrl>
    {


        @Override
        public SpecUrl deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            SpecUrl result = new SpecUrl();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_ARRAY) {
                while (parser.nextToken()!= JsonToken.END_ARRAY) {
                    token = parser.currentToken();
                    if (token == JsonToken.VALUE_STRING) {
                        result.add(parser.readValueAs(String.class));
                    } else {
                        deserializationContext.handleUnexpectedToken(String.class, parser);
                    }
                }
            } else {
                if (token == JsonToken.VALUE_STRING) {
                    result.add(parser.readValueAs(String.class));
                } else {
                    deserializationContext.handleUnexpectedToken(String.class, parser);
                }
            }
            return result;
        }

    }

}
