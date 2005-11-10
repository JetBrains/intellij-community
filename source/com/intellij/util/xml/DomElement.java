/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface DomElement {

  @Nullable
  XmlTag getXmlTag();

  DomFileElement getRoot();

  DomElement getParent();

  XmlTag ensureTagExists();

  void undefine() throws IllegalAccessException, InstantiationException;

  boolean isValid();

  DomMethodsInfo getMethodsInfo();

  String getTagName();

  void acceptChildren(DomElementVisitor visitor);
}
