/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.ui;

import com.intellij.openapi.options.Configurable;


/**
 * User: anna
 * Date: 26-May-2006
 */
public interface NamedConfigurable<T> extends Configurable {
  void setDisplayName(String name);
  T getEditableObject();
  String getBannerSlogan();
}
