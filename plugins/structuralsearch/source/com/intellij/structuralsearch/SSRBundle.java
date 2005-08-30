package com.intellij.structuralsearch;

import com.intellij.CommonBundle;

import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.text.MessageFormat;

@SuppressWarnings({"HardCodedStringLiteral"})
public class SSRBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.structuralsearch.SSRBundle");

  private SSRBundle() {}

  public static String message(String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}