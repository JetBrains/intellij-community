/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class VirtualDomParentStrategy implements DomParentStrategy {
  private final DomInvocationHandler myParentHandler;
  private long myModCount;
  private final PsiFile myModificationTracker;

  public VirtualDomParentStrategy(@NotNull final DomInvocationHandler parentHandler) {
    myParentHandler = parentHandler;
    myModificationTracker = parentHandler.getFile();
    myModCount = getModCount();
  }

  private long getModCount() {
    return myModificationTracker.getModificationStamp();
  }

  @Override
  @NotNull
  public DomInvocationHandler getParentHandler() {
    return myParentHandler;
  }

  @Override
  public XmlElement getXmlElement() {
    return null;
  }

  @Override
  @NotNull
  public synchronized DomParentStrategy refreshStrategy(final DomInvocationHandler handler) {
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
  @NotNull
  public DomParentStrategy setXmlElement(@NotNull final XmlElement element) {
    return new PhysicalDomParentStrategy(element, DomManagerImpl.getDomManager(element.getProject()));
  }

  @Override
  @NotNull
  public synchronized DomParentStrategy clearXmlElement() {
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

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof VirtualDomParentStrategy)) return false;

    final VirtualDomParentStrategy that = (VirtualDomParentStrategy)o;

    if (!myParentHandler.equals(that.myParentHandler)) return false;

    return true;
  }

  public int hashCode() {
    return myParentHandler.hashCode();
  }
}
