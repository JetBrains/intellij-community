/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class RootDomParentStrategy implements DomParentStrategy {
  private final DomFileElementImpl myFileElement;

  public RootDomParentStrategy(final DomFileElementImpl fileElement) {
    myFileElement = fileElement;
  }

  @NotNull
  public DomInvocationHandler getParentHandler() {
    throw new UnsupportedOperationException("Method getParentHandler is not yet implemented in " + getClass().getName());
  }

  public XmlTag getXmlElement() {
    return myFileElement.getRootTag();
  }

  @NotNull
  public DomParentStrategy refreshStrategy(final DomInvocationHandler handler) {
    return this;
  }

  @NotNull
  public DomParentStrategy setXmlElement(@NotNull final XmlElement element) {
    return this;
  }

  @NotNull
  public DomParentStrategy clearXmlElement() {
    return this;
  }

  public boolean isValid() {
    return myFileElement.isValid();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof RootDomParentStrategy)) return false;

    final RootDomParentStrategy that = (RootDomParentStrategy)o;

    if (!myFileElement.equals(that.myFileElement)) return false;

    return true;
  }

  public int hashCode() {
    return myFileElement.hashCode();
  }
}
