/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package org.jetbrains.idea.svn;

import com.intellij.CommonBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;

public class SvnBundle {
  @NonNls private static final String BUNDLE = "org.jetbrains.idea.svn.SvnBundle";

  private SvnBundle() {
  }

  public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
    return CommonBundle.message(ResourceBundle.getBundle(BUNDLE), key, params);
  }
}
