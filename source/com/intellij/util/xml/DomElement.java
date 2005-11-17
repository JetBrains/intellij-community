/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.reflect.DomMethodsInfo;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public interface DomElement {

  @Nullable
  XmlTag getXmlTag();

  DomFileElement<?> getRoot();

  DomElement getParent();

  XmlTag ensureTagExists();

  void undefine() throws IllegalAccessException, InstantiationException;

  boolean isValid();

  DomMethodsInfo getMethodsInfo();

  String getTagName();

  void acceptChildren(DomElementVisitor visitor);

  DomManager getManager();

  Type getDomElementType();

  DomNameStrategy getNameStrategy();

  String getCommonPresentableName();

  GlobalSearchScope getResolveScope();
}
