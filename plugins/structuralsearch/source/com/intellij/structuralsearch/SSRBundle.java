package com.intellij.structuralsearch;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

@SuppressWarnings({"HardCodedStringLiteral"})
public class SSRBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.structuralsearch.SSRBundle");

  private SSRBundle() {}

  public static String message(@PropertyKey(resourceBundle = "com.intellij.structuralsearch.SSRBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}