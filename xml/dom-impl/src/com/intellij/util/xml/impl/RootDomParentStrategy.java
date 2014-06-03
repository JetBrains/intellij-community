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

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
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

  @Override
  @NotNull
  public DomInvocationHandler getParentHandler() {
    throw new UnsupportedOperationException("Method getParentHandler is not yet implemented in " + getClass().getName());
  }

  @Override
  public XmlTag getXmlElement() {
    return myFileElement.getRootTag();
  }

  @Override
  @NotNull
  public DomParentStrategy refreshStrategy(final DomInvocationHandler handler) {
    return this;
  }

  @Override
  @NotNull
  public DomParentStrategy setXmlElement(@NotNull final XmlElement element) {
    return this;
  }

  @Override
  @NotNull
  public DomParentStrategy clearXmlElement() {
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
