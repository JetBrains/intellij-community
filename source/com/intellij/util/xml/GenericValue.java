/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface GenericValue<T> extends DomElement{

  @TagValue
  @Nullable
  String getStringValue();

  @TagValue
  void setStringValue(String value);

  @Nullable
  T getValue();

  void setValue(T value);
}
