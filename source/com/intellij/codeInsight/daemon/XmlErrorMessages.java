/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.daemon;

import com.intellij.CommonBundle;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class XmlErrorMessages {
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("com.intellij.codeInsight.daemon.XmlErrorMessages");

  private XmlErrorMessages() {}

  public static String message(String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
