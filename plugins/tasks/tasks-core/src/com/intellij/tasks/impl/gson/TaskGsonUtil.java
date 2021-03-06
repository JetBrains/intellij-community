// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.impl.gson;

import com.google.gson.*;
import com.intellij.tasks.impl.TaskUtil;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.NullCheckingFactory;
import org.jetbrains.io.mandatory.RestModel;

import java.lang.reflect.Type;
import java.util.Date;

/**
 * @author Mikhail Golubev
 */
public final class TaskGsonUtil {

  private TaskGsonUtil() {
  }

  public static final JsonDeserializer<Date> DATE_DESERIALIZER = new JsonDeserializer<>() {
    @Override
    public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      return TaskUtil.parseDate(json.getAsString());
    }
  };

  /**
   * Create default GsonBuilder used to create Gson factories across various task repository implementations.
   * It uses {@link TaskUtil#formatDate(Date)} to parse dates and {@link NullCheckingFactory}
   * to preserve null-safety of objects returned by server.
   *
   * @see NullCheckingFactory
   * @see Mandatory
   * @see RestModel
   * @return described builder
   */
  public static GsonBuilder createDefaultBuilder() {
    return new GsonBuilder()
      .registerTypeAdapter(Date.class, DATE_DESERIALIZER)
      .registerTypeAdapterFactory(NullCheckingFactory.INSTANCE);
  }
}
