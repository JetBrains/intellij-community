/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * @author peter
 */
public interface DomMethodsInfo {

  Collection<Method> getFixedChildrenGetterMethods();
  Collection<Method> getCollectionChildrenGetterMethods();

  int getFixedChildIndex(Method method);

  String getTagName(Method method);
}
