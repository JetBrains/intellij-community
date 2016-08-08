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
    public String format(final String value) {
      return value;
    }
  };

  private static final Formatter STR_FORMATTER = new Formatter() {
    public String format(final String value) {
      return new StringBuilder(value.length() + 2).append('\'').append(StringUtil.replace(value, "'", "\\'").replace("\\", "\\\\")).append(
        '\'').toString();
    }
  };

  private static final Formatter UNI_FORMATTER = new Formatter() {
    public String format(final String value) {
      return new StringBuilder(value.length() + 3).append("u'").append(StringUtil.replace(value, "'", "\\'").replace("\\", "\\\\")).append('\'').toString();
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
