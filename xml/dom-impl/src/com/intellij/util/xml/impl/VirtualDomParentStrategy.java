// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

public class VirtualDomParentStrategy implements DomParentStrategy {
  private final DomInvocationHandler myParentHandler;
  private long myModCount;
  private final PsiFile myModificationTracker;

  public VirtualDomParentStrategy(final @NotNull DomInvocationHandler parentHandler) {
    myParentHandler = parentHandler;
    myModificationTracker = parentHandler.getFile();
    myModCount = getModCount();
  }

  private long getModCount() {
    return myModificationTracker.getModificationStamp();
  }

  @Override
  public @NotNull DomInvocationHandler getParentHandler() {
    return myParentHandler;
  }

  @Override
  public XmlElement getXmlElement() {
    return null;
  }

  @Override
  public synchronized @NotNull DomParentStrategy refreshStrategy(final DomInvocationHandler handler) {
    final long modCount = getModCount();
    if (modCount != myModCount) {
      if (!myParentHandler.isValid()) return this;

      final XmlElement xmlElement = handler.recomputeXmlElement(myParentHandler);
      if (xmlElement != null) {
        return new PhysicalDomParentStrategy(xmlElement, DomManagerImpl.getDomManager(xmlElement.getProject()));
      }
      myModCount = modCount;
    }
    return this;
  }

  @Override
  public @NotNull DomParentStrategy setXmlElement(final @NotNull XmlElement element) {
    return new PhysicalDomParentStrategy(element, DomManagerImpl.getDomManager(element.getProject()));
  }

  @Override
  public synchronized @NotNull DomParentStrategy clearXmlElement() {
    myModCount = getModCount();
    return this;
  }

  @Override
  public synchronized String checkValidity() {
    if (getModCount() == myModCount) {
      return null;
    }
    return "mod count changed";
  }

  @Override
  public XmlFile getContainingFile(DomInvocationHandler handler) {
    return DomImplUtil.getFile(handler);
  }

  @Override
  public boolean isPhysical() {
    return false;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof VirtualDomParentStrategy that)) return false;

    if (!myParentHandler.equals(that.myParentHandler)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myParentHandler.hashCode();
  }
}
