// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.util.text.StringUtil;

import java.util.HashMap;
import java.util.Map;


// todo: extensive types support
public class PyTypeHandler {

  private interface Formatter {
    String format(String value);
  }

  private static final Formatter DEFAULT_FORMATTER = new Formatter() {
    @Override
    public String format(final String value) {
      return value;
    }
  };

  private static final Formatter STR_FORMATTER = new Formatter() {
    @Override
    public String format(final String value) {
      return "'" +
             StringUtil.replace(value, "\\", "\\\\").replace("'", "\\'") +
             '\'';
    }
  };

  private static final Formatter UNI_FORMATTER = new Formatter() {
    @Override
    public String format(final String value) {
      return "u'" +
             StringUtil.replace(value, "\\", "\\\\").replace("'", "\\'") +
             '\'';
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
