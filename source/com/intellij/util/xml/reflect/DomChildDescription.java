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
public interface DomChildDescription {
  String getTagName();
  List<DomElement> getChildren(DomElement element);
  Type getType();
}
