
package com.intellij.bcd.json;

import java.io.IOException;
import java.util.ArrayList;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = Notes.MyDeserializer.class)
public class Notes
    extends ArrayList<String>
{


    public static class MyDeserializer
        extends JsonDeserializer<Notes>
    {


        @Override
        public Notes deserialize(JsonParser parser, DeserializationContext deserializationContext)
            throws IOException
        {
            Notes result = new Notes();
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
