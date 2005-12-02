/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface GenericValue<T> {

  @TagValue
  @Nullable
  String getStringValue();

  @Nullable
  T getValue();

}