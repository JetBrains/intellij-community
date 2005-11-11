/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public interface DomMethodsInfo {

  Collection<Method> getFixedChildrenGetterMethods();
  Collection<Method> getCollectionChildrenGetterMethods();

  int getFixedChildIndex(Method method);

  String getTagName(Method method);

  Method getCollectionGetMethod(String tagName);
  Method getCollectionAddMethod(String tagName);
  Method getCollectionIndexedAddMethod(String tagName);

  @NotNull
  List<DomChildDescription> getChildrenDescriptions();

  @Nullable
  DomFixedChildDescription getFixedChildDescription(String tagName);

  @Nullable
  DomCollectionChildDescription getCollectionChildDescription(String tagName);
}
