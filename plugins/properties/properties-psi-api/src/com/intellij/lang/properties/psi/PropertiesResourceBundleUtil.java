// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class PropertiesResourceBundleUtil {
  private static final char ESCAPE_SYMBOL = '\\';

  /**
   * Allows to map given 'raw' property value text to the 'user-friendly' text to show at the resource bundle editor.
   * <p/>
   * <b>Note:</b> please refer to {@link java.util.Properties#store(java.io.Writer, String)} contract for the property value escape rules.
   *
   * @param text  'raw' property value text
   * @return      'user-friendly' text to show at the resource bundle editor
   */
  public static @NotNull String fromPropertyValueToValueEditor(@NotNull String text) {
    StringBuilder buffer = new StringBuilder();
    boolean escaped = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == ESCAPE_SYMBOL && !escaped && (i == text.length() - 1 || (text.charAt(i + 1) != 'u' && text.charAt(i + 1) != 'U'))) {
        escaped = true;
        continue;
      }
      if (escaped && (c == 'n' || c == 'r')) {
        buffer.append(ESCAPE_SYMBOL);
      }
      buffer.append(c);
      escaped = false;
    }
    return buffer.toString();
  }

  /**
   * Converts property value from given {@code valueFormat} to 'raw' format (how it should be stored in *.properties file)
   */
  public static @NotNull String convertValueToFileFormat(@NotNull String value, char delimiter, @NotNull PropertyKeyValueFormat valueFormat) {
    if (valueFormat == PropertyKeyValueFormat.FILE) return value;

    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);

      if (c == '\n' || c == '\r') {
        buffer.append(ESCAPE_SYMBOL);
        if (valueFormat == PropertyKeyValueFormat.MEMORY) {
          buffer.append(c == '\n' ? 'n' : 'r');
        }
        else {
          buffer.append(c);
        }
      }
      else if ((i == 0 && (c == ' ' || c == '\t')) // Leading white space
               || (delimiter == ' ' && (c == '=' || c == ':' /* special symbol */)))  {
        buffer.append(ESCAPE_SYMBOL);
        buffer.append(c);
      }
      else if (c == ESCAPE_SYMBOL) {
        if (i + 1 >= value.length() || !isEscapedChar(value.charAt(i + 1)) || valueFormat == PropertyKeyValueFormat.MEMORY) {
          buffer.append(ESCAPE_SYMBOL);
        }
        buffer.append(c);
      }
      else {
        buffer.append(c);
      }
    }
    return buffer.toString();
  }

  private static boolean isEscapedChar(char nextChar) {
    return nextChar == 'n' || nextChar == 'r' || nextChar == 'u' || nextChar == 'U';
  }
}
