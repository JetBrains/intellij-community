/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.completion;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class CompletionBundle {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.CompletionBundle");

  private CompletionBundle() {}

  public static String message(@PropertyKey(resourceBundle = "messages.CompletionBundle") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
