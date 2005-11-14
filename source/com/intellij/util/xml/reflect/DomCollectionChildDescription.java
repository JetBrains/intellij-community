/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public interface DomCollectionChildDescription extends DomChildrenDescription {
  Method getGetterMethod();
  Method getIndexedAdderMethod();
  Method getAdderMethod();

  DomElement addValue(DomElement element);
  DomElement addValue(DomElement element, int index);
}
