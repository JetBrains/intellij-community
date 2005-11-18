/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomAttributeValue;
import com.intellij.util.xml.DomElement;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public interface DomAttributeChildDescription extends DomChildrenDescription{
  DomAttributeValue getDomAttributeValue(DomElement parent);

  Method getGetterMethod();
}
