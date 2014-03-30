/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.networking;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 3/25/13
 * Time: 5:44 PM
 */
public class SSLProtocolExceptionParser {
  private final String myMessage;
  private String myParsedMessage;
  private byte myFieldValue;
  private String myFieldName;

  public SSLProtocolExceptionParser(@NotNull String message) {
    myMessage = message;
  }

  public void parse() {
    myParsedMessage = myMessage;
    myFieldValue = 0;
    myFieldName = null;

    final List<String> words = StringUtil.split(myMessage.trim(), " ");
    if (words.isEmpty()) return;
    // we'll try to parse by last word - it's just an attempt so ok if failed - just will show the real message

    final String[] possiblePlaces = {"com.sun.net.ssl.internal.ssl.Alerts", "sun.security.ssl.Alerts"};
    for (String place : possiblePlaces) {
      try {
        final Class<?> clazz = Class.forName(place);
        if (tryByStaticField(clazz, words.get(words.size() - 1))) {
          return;
        }
      }
      catch (ClassNotFoundException e) {
        //
      }
    }
  }

  public String getParsedMessage() {
    return myParsedMessage;
  }

  private boolean tryByStaticField(Class<?> clazz, String word) {
    try {
      final Field field = clazz.getDeclaredField("alert_" + word);
      field.setAccessible(true);
      myFieldValue = field.getByte(clazz);
      myFieldName = field.getName();
      myParsedMessage = "SSLProtocolException: alert code: " + Byte.toString(myFieldValue) + " alert name: " + myFieldName +
                        ", original message: " + myMessage;
      if ("alert_unrecognized_name".equals(myFieldName)) {
        myParsedMessage += "\nThis may be JDK bug 7127374 : JSSE creates SSLProtocolException on (common) warning: unrecognized_name for SNI";
      }
    }
    catch (NoSuchFieldException e) {
      return false;
    }
    catch (IllegalAccessException e) {
      return false;
    }
    return true;
  }
}
