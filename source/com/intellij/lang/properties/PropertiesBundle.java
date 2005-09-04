/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.lang.properties;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;
import com.intellij.CommonBundle;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Sep 4, 2005
 * Time: 10:12:06 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesBundle {
  @NonNls protected static final String PATH_TO_BUNDLE = "com.intellij.lang.properties.PropertiesBundle";
  private final static java.util.ResourceBundle ourResourceBundle = java.util.ResourceBundle.getBundle(PATH_TO_BUNDLE);

  public static String message(@PropertyKey(resourceBundle = "com.intellij.lang.properties.PropertiesBundle") String key, Object... params) {
    return CommonBundle.message(ourResourceBundle, key, params);
  }

}
