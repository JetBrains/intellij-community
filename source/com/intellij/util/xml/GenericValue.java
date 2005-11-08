/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public interface GenericValue<T> extends DomElement{

  @TagValue
  String getStringValue();

  T getValue();

  void setValue(T value);
}
