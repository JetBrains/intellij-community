/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.completion;

import com.intellij.CommonBundle;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class CompletionBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.codeInsight.completion.CompletionBundle");

  private CompletionBundle() {}

  public static String message(String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
