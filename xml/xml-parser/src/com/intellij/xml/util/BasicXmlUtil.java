// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class BasicXmlUtil {
  private static final Logger LOG = Logger.getInstance(BasicXmlUtil.class);

  private BasicXmlUtil() { }

  public static @NotNull CharSequence getLocalName(@NotNull CharSequence tagName) {
    int pos = StringUtil.indexOf(tagName, ':');
    if (pos == -1) {
      return tagName;
    }
    return tagName.subSequence(pos + 1, tagName.length());
  }

  public static char getCharFromEntityRef(@NonNls @NotNull String text) {
    try {
      if (text.charAt(1) != '#') {
        text = text.substring(1, text.length() - 1);
        char c = BasicXmlTagUtil.getCharacterByEntityName(text);
        if (c == 0) {
          LOG.error("Unknown entity: " + text);
        }
        return c == 0 ? ' ' : c;
      }
      text = text.substring(2, text.length() - 1);
    }
    catch (StringIndexOutOfBoundsException e) {
      LOG.error("Cannot parse ref: '" + text + "'", e);
    }
    try {
      int code;
      if (StringUtil.startsWithChar(text, 'x')) {
        text = text.substring(1);
        code = Integer.parseInt(text, 16);
      }
      else {
        code = Integer.parseInt(text);
      }
      return (char)code;
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }
}
