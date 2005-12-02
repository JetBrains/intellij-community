/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

/**
 * @author peter
 */
public interface GenericDomValue<T> extends DomElement, GenericValue<T>{

  @TagValue
  void setStringValue(String value);

  void setValue(T value);

}
