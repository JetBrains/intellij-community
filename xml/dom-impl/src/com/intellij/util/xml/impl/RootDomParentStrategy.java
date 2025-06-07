// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class RootDomParentStrategy implements DomParentStrategy {
  private final DomFileElementImpl myFileElement;

  public RootDomParentStrategy(final DomFileElementImpl fileElement) {
    myFileElement = fileElement;
  }

  @Override
  public @NotNull DomInvocationHandler getParentHandler() {
    throw new UnsupportedOperationException("Method getParentHandler is not yet implemented in " + getClass().getName());
  }

  @Override
  public XmlTag getXmlElement() {
    return myFileElement.getRootTag();
  }

  @Override
  public @NotNull DomParentStrategy refreshStrategy(final DomInvocationHandler handler) {
    return this;
  }

  @Override
  public @NotNull DomParentStrategy setXmlElement(final @NotNull XmlElement element) {
    return this;
  }

  @Override
  public @NotNull DomParentStrategy clearXmlElement() {
    return this;
  }

  @Override
  public String checkValidity() {
    return myFileElement.checkValidity();
  }

  @Override
  public XmlFile getContainingFile(DomInvocationHandler handler) {
    return myFileElement.getFile();
  }

  @Override
  public boolean isPhysical() {
    return true;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof RootDomParentStrategy that)) return false;

    if (!myFileElement.equals(that.myFileElement)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myFileElement.hashCode();
  }
}
