/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.lang.properties;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

public class PropertiesBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "messages.PropertiesBundle";

  private PropertiesBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = PATH_TO_BUNDLE) String key, Object... params) {
    return CommonBundle.message(java.util.ResourceBundle.getBundle(PATH_TO_BUNDLE), key, params);
  }
}
