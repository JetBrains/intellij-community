// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.containers.ObjectIntMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

@ApiStatus.Internal
public final class BasicXmlTagUtil {
  private static final ObjectIntMap<String> ourCharacterEntities = new ObjectIntHashMap<>(6);

  static {
    ourCharacterEntities.put("lt", '<');
    ourCharacterEntities.put("gt", '>');
    ourCharacterEntities.put("apos", '\'');
    ourCharacterEntities.put("quot", '\"');
    ourCharacterEntities.put("nbsp", '\u00a0');
    ourCharacterEntities.put("amp", '&');
  }

  /**
   * if text contains XML-sensitive characters (<,>), quote text with ![CDATA[ ... ]]
   *
   * @return quoted text
   */
  public static String getCDATAQuote(String text) {
    if (text == null) return null;
    String offensiveChars = "<>&\n";
    final int textLength = text.length();
    if (textLength > 0 && (Character.isWhitespace(text.charAt(0)) || Character.isWhitespace(text.charAt(textLength - 1)))) {
      return "<![CDATA[" + text + "]]>";
    }
    for (int i = 0; i < offensiveChars.length(); i++) {
      char c = offensiveChars.charAt(i);
      if (text.indexOf(c) != -1) {
        return "<![CDATA[" + text + "]]>";
      }
    }
    return text;
  }

  public static String getInlineQuote(String text) {
    if (text == null) return null;
    String offensiveChars = "<>&";
    for (int i = 0; i < offensiveChars.length(); i++) {
      char c = offensiveChars.charAt(i);
      if (text.indexOf(c) != -1) {
        return "<![CDATA[" + text + "]]>";
      }
    }
    return text;
  }


  public static CharSequence composeTagText(@NonNls String tagName, @NonNls String tagValue) {
    StringBuilder builder = new StringBuilder();
    builder.append('<').append(tagName);
    if (StringUtil.isEmpty(tagValue)) {
      builder.append("/>");
    }
    else {
      builder.append('>').append(getCDATAQuote(tagValue)).append("</").append(tagName).append('>');
    }
    return builder;
  }

  public static String[] getCharacterEntityNames() {
    return ArrayUtilRt.toStringArray(ourCharacterEntities.keySet());
  }

  public static char getCharacterByEntityName(String entityName) {
    int c = ourCharacterEntities.get(entityName);
    return c == -1 ? 0 : (char)c;
  }
}
