package org.jetbrains.plugins.textmate.plist;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.textmate.plist.PListValue.value;

public final class JsonPlistReader implements PlistReader {
  @Override
  public Plist read(@NotNull InputStream inputStream) throws IOException {
    return internalRead(createJsonReader().readValue(new InputStreamReader(inputStream, StandardCharsets.UTF_8), Object.class));
  }

  private static Plist internalRead(Object root) throws IOException {
    if (!(root instanceof Map)) {
      throw new IOException("Unknown json format. Root element is '" + root + "'");
    }

    //noinspection unchecked
    return (Plist)readDict((Map<String, Object>)root).getValue();
  }

  private static PListValue readDict(@NotNull Map<String, Object> map) {
    Plist dict = new Plist();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      PListValue value = readValue(entry.getValue());
      if (value != null) {
        dict.setEntry(entry.getKey(), value);
      }
    }

    return value(dict, PlistValueType.DICT);
  }

  @Nullable
  private static PListValue readValue(Object value) {
    if (value instanceof Map) {
      //noinspection unchecked
      return readDict((Map<String, Object>)value);
    }
    else if (value instanceof ArrayList) {
      return readArray((ArrayList<?>)value);
    }
    else {
      return readBasicValue(value);
    }
  }

  private static PListValue readArray(ArrayList<?> list) {
    List<Object> result = new ArrayList<>();
    for (Object o : list) {
      PListValue value = readValue(o);
      if (value != null) {
        result.add(value);
      }
    }
    return value(result, PlistValueType.ARRAY);
  }

  @Nullable
  private static PListValue readBasicValue(Object value) {
    if (value instanceof String) {
      return value(value, PlistValueType.STRING);
    }
    else if (value instanceof Boolean) {
      return value(value, PlistValueType.BOOLEAN);
    }
    else if (value instanceof Integer) {
      return value(Long.valueOf((Integer)value), PlistValueType.INTEGER);
    }
    else if (value instanceof Double) {
      return value(value, PlistValueType.REAL);
    }
    return null;
  }

  public static ObjectMapper createJsonReader() {
    JsonFactory factory = JsonFactory.builder()
      .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
      .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
      .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
      .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
      .build();
    return new ObjectMapper(factory).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
}
