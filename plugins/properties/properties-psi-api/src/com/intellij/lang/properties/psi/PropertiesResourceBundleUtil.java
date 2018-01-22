// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.psi;

import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class PropertiesResourceBundleUtil {
  private static final TIntHashSet SYMBOLS_TO_ESCAPE = new TIntHashSet(new int[]{'=', ':'});
  private static final char        ESCAPE_SYMBOL     = '\\';


  /**
   * Allows to map given 'raw' property value text to the 'user-friendly' text to show at the resource bundle editor.
   * <p/>
   * <b>Note:</b> please refer to {@link java.util.Properties#store(java.io.Writer, String)} contract for the property value escape rules.
   *
   * @param text  'raw' property value text
   * @return      'user-friendly' text to show at the resource bundle editor
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  @NotNull
  public static String fromPropertyValueToValueEditor(@NotNull String text) {
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
   * Perform reverse operation to {@link #fromPropertyValueToValueEditor(String)}.
   *
   * @param text  'user-friendly' text shown to the user at the resource bundle editor
   * @param delimiter
   * @return      'raw' value to store at the *.properties file
   */
  @NotNull
  public static String fromValueEditorToPropertyValue(@NotNull String text, char delimiter) {
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);

      if ((i == 0 && (c == ' ' || c == '\t')) // Leading white space
          || c == '\n' || c == '\r' // Multi-line value
          || (delimiter == ' ' && SYMBOLS_TO_ESCAPE.contains(c)))   // Special symbol
      {
        buffer.append(ESCAPE_SYMBOL);
      }
      else if (c == ESCAPE_SYMBOL) {           // Escaped 'escape' symbol)
        if (text.length() > i + 1) {
          final char nextChar = text.charAt(i + 1);
          if (nextChar != 'n' && nextChar != 'r' && nextChar != 'u' && nextChar != 'U') {
            buffer.append(ESCAPE_SYMBOL);
          }
        } else {
          buffer.append(ESCAPE_SYMBOL);
        }
      }
      buffer.append(c);
    }
    return buffer.toString();
  }
}
