/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.analysis;

import com.intellij.CommonBundle;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class AnalysisScopeBundle {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.analysis.AnalysisScopeBundle");

  public static String message(String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
