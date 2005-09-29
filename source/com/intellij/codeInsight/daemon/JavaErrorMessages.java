/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.daemon;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class JavaErrorMessages {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.JavaErrorMessages");

  private JavaErrorMessages() {}

  public static String message(@PropertyKey(resourceBundle = "messages.JavaErrorMessages") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
