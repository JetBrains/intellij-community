/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class CodeInsightBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.codeInsight.CodeInsightBundle");

  private CodeInsightBundle() {}

  public static String message(@PropertyKey String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
