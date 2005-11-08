/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.impl.DomFileElementImpl;
import com.intellij.util.xml.impl.DomMethodsInfo;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface DomElement {

  @Nullable
  XmlTag getXmlTag();

  DomFileElementImpl getRoot();

  DomElement getParent();

  XmlTag ensureTagExists();

  void undefine() throws IllegalAccessException, InstantiationException;

  boolean isValid();

  DomMethodsInfo getMethodsInfo();
}
