package com.intellij.tasks.impl.gson;

import com.google.gson.*;
import com.intellij.tasks.impl.TaskUtil;

import java.lang.reflect.Type;
import java.util.Date;

/**
 * @author Mikhail Golubev
 */
public class GsonUtil {

  private GsonUtil() {
    // empty
  }

  public static final JsonDeserializer<Date> DATE_DESERIALIZER = new JsonDeserializer<Date>() {
    @Override
    public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      return TaskUtil.parseDate(json.getAsString());
    }
  };

  /**
   * Create default GsonBuilder used to create Gson factories across various task repository implementations.
   * It uses {@link TaskUtil#formatDate(java.util.Date)} to parse dates and {@link NullCheckingFactory}
   * to preserve null-safety of objects returned by server.
   *
   * @see com.intellij.tasks.impl.gson.NullCheckingFactory
   * @see com.intellij.tasks.impl.gson.Mandatory
   * @see com.intellij.tasks.impl.gson.RestModel
   * @return described builder
   */
  public static GsonBuilder createDefaultBuilder() {
    return new GsonBuilder()
      .registerTypeAdapter(Date.class, DATE_DESERIALIZER)
      .registerTypeAdapterFactory(NullCheckingFactory.INSTANCE);
  }

  /**
   * @deprecated use {@link #createDefaultBuilder} instead
   */
  @Deprecated
  public static GsonBuilder installDateDeserializer(GsonBuilder builder) {
    return builder.registerTypeAdapter(Date.class, DATE_DESERIALIZER);
  }
}
