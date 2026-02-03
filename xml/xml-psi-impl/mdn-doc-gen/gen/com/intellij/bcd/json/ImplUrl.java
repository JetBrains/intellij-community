
package com.intellij.bcd.json;

import java.io.IOException;
import java.util.ArrayList;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = ImplUrl.MyDeserializer.class)
public class ImplUrl
    extends ArrayList<String>
{


    public static class MyDeserializer
        extends JsonDeserializer<ImplUrl>
    {


        @Override
        public ImplUrl deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            ImplUrl result = new ImplUrl();
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
