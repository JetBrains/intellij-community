/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import com.intellij.util.xml.DomElement;

import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public interface DomChildrenDescription {
  String getTagName();
  List<? extends DomElement> getValues(DomElement element);
  Type getType();
}
