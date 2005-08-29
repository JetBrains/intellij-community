/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package org.jetbrains.idea.svn;

import com.intellij.CommonBundle;

import java.util.ResourceBundle;

import org.jetbrains.annotations.NonNls;

public class SvnBundle {
  @NonNls private final static ResourceBundle ourBundle = ResourceBundle.getBundle("org.jetbrains.idea.svn.SvnBundle");

  public static String message(@NonNls String key, Object... params) {
    return CommonBundle.message(ourBundle, key, params);
  }
}
