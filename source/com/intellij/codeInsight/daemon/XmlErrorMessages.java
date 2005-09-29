/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.daemon;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.NonNls;

import java.util.ResourceBundle;

/**
 * @author max
 */
public class XmlErrorMessages {
  @NonNls private static final ResourceBundle ourBundle = ResourceBundle.getBundle("messages.XmlErrorMessages");

  private XmlErrorMessages() {}

  public static String message(@PropertyKey(resourceBundle = "messages.XmlErrorMessages") String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
