package com.intellij.structuralsearch;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

public class SSRBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.SSRBundle");

  private SSRBundle() {}

  public static String message(@PropertyKey(resourceBundle = "messages.SSRBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}