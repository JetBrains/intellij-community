// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


// todo: extensive types support
public final class PyTypeHandler {

  private interface Formatter {
    String format(String value);
  }

  private static final Formatter DEFAULT_FORMATTER = new Formatter() {
    @Override
    public String format(final String value) {
      return value;
    }
  };

  /**
   * Like in python3 interpreter:
   *  HEX | DEX | Symbol
   * \x09 |  9  | \t
   * \x0a | 10  | \n
   * \x0d | 13  | \r
   * Another chars from (0, 32) and (127, 160) is non-printable characters, and we should print their code
   * */
  private static boolean isNonPrintable(char c) {
    return c < 32 && c != 9 && c != 10 && c != 13 || c > 127 && c < 160;
  }

  private static @NotNull String charToHEX(char c) {
    return String.format("%02x", (int) c);
  }

  private static @NotNull String processNonPrintableChars(@NotNull String string) {
    StringBuilder result = new StringBuilder();
    for (char c : string.toCharArray()) {
      if (isNonPrintable(c)) {
        result.append("\\x");
        result.append(charToHEX(c));
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  private static final Formatter STR_FORMATTER = new Formatter() {
    @Override
    public String format(final String value) {
      String escapedString = StringUtil.replace(value, "\\", "\\\\");

      if (value.contains("'")) {
        return "\"" + processNonPrintableChars(escapedString) + "\"";
      }
      else {
        return "'" + processNonPrintableChars(escapedString) + '\'';
      }
    }
  };

  private static final Formatter UNI_FORMATTER = new Formatter() {
    @Override
    public String format(final String value) {
      String escapedString = StringUtil.replace(value, "\\", "\\\\");
      if (value.contains("'")) {
        return "u\"" + escapedString + "\"";
      }
      else {
        return "u'" + escapedString + '\'';
      }
    }
  };

  private static final Map<String, Formatter> FORMATTERS;
  static {
    FORMATTERS = new HashMap<>();
    FORMATTERS.put("str", STR_FORMATTER);
    FORMATTERS.put("unicode", UNI_FORMATTER);
    //numpy types
    FORMATTERS.put("string_", STR_FORMATTER);
    FORMATTERS.put("unicode_", UNI_FORMATTER);
  }

  private PyTypeHandler() { }

  public static String format(final PyDebugValue var) {
    return format(var.getType(), var.getValue());
  }

  public static String format(String type, String value) {
    Formatter formatter = FORMATTERS.get(type);
    if (formatter == null) formatter = DEFAULT_FORMATTER;
    return formatter.format(value);
  }
}
