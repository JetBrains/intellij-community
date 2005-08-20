package com.intellij.structuralsearch;

import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;

@SuppressWarnings({"HardCodedStringLiteral"})
public class SSRBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.structuralsearch.SSRBundle");

  private SSRBundle() {}

  public static String message(String key, Object... params) {
    String value;
    try {
      value = ourBundle.getString(key);
    }
    catch (MissingResourceException e) {
      return "!" + key + "!";
    }

    if (params.length > 0) {
      return MessageFormat.format(value, params);
    }

    return value;
  }
}