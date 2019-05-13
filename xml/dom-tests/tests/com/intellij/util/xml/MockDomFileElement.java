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
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * @author peter
 */
public class MockDomFileElement extends UserDataHolderBase implements DomFileElement<DomElement> {
  private long myModCount = 0;
  private DomFileDescription<DomElement> myFileDescription;

  public void setFileDescription(final DomFileDescription<DomElement> fileDescription) {
    myFileDescription = fileDescription;
  }

  @Override
  @NotNull
  public XmlFile getFile() {
    throw new UnsupportedOperationException("Method getFile is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public XmlFile getOriginalFile() {
    throw new UnsupportedOperationException("Method getOriginalFile is not yet implemented in " + getClass().getName());
  }

  @Override
  public XmlTag getRootTag() {
    throw new UnsupportedOperationException("Method getRootTag is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public DomElement getRootElement() {
    throw new UnsupportedOperationException("Method getRootElement is not yet implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public Class<DomElement> getRootElementClass() {
    throw new UnsupportedOperationException("Method getRootElementType is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public DomFileDescription<DomElement> getFileDescription() {
    return myFileDescription;
  }

  @Override
  @Nullable
  public XmlTag getXmlTag() {
    throw new UnsupportedOperationException("Method getXmlTag is not yet implemented in " + getClass().getName());
  }

  @NotNull
  public <T extends DomElement> DomFileElement<T> getRoot() {
    return (DomFileElement<T>)this;
  }

  @Override
  @Nullable
  public DomElement getParent() {
    throw new UnsupportedOperationException("Method getParent is not yet implemented in " + getClass().getName());
  }

  @Override
  public XmlTag ensureTagExists() {
    throw new UnsupportedOperationException("Method ensureTagExists is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public XmlElement getXmlElement() {
    throw new UnsupportedOperationException("Method getXmlElement is not yet implemented in " + getClass().getName());
  }

  @Override
  public XmlElement ensureXmlElementExists() {
    throw new UnsupportedOperationException("Method ensureXmlElementExists is not yet implemented in " + getClass().getName());
  }

  @Override
  public void undefine() {
    throw new UnsupportedOperationException("Method undefine is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean isValid() {
    throw new UnsupportedOperationException("Method isValid is not yet implemented in " + getClass().getName());
  }

  @Override
  public boolean exists() {
    throw new UnsupportedOperationException("Method exists is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public DomGenericInfo getGenericInfo() {
    throw new UnsupportedOperationException("Method getGenericInfo is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  @NonNls
  public String getXmlElementName() {
    throw new UnsupportedOperationException("Method getXmlElementName is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  @NonNls
  public String getXmlElementNamespace() {
    throw new UnsupportedOperationException("Method getXmlElementNamespace is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  @NonNls
  public String getXmlElementNamespaceKey() {
    throw new UnsupportedOperationException("Method getXmlElementNamespaceKey is not yet implemented in " + getClass().getName());
  }

  @Override
  public void accept(final DomElementVisitor visitor) {
    throw new UnsupportedOperationException("Method accept is not yet implemented in " + getClass().getName());
  }

  @Override
  public void acceptChildren(DomElementVisitor visitor) {
  }

  @Override
  @NotNull
  public DomManager getManager() {
    throw new UnsupportedOperationException("Method getManager is not yet implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public Type getDomElementType() {
    throw new UnsupportedOperationException("Method getDomElementType is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public AbstractDomChildrenDescription getChildDescription() {
    throw new UnsupportedOperationException("Method getChildDescription is not yet implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public DomNameStrategy getNameStrategy() {
    throw new UnsupportedOperationException("Method getNameStrategy is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public ElementPresentation getPresentation() {
    throw new UnsupportedOperationException("Method getPresentation is not yet implemented in " + getClass().getName());
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    throw new UnsupportedOperationException("Method getResolveScope is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict) {
    throw new UnsupportedOperationException("Method getParentOfType is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public Module getModule() {
    throw new UnsupportedOperationException("Method getModule is not yet implemented in " + getClass().getName());
  }

  @Override
  public void copyFrom(DomElement other) {
    throw new UnsupportedOperationException("Method copyFrom is not yet implemented in " + getClass().getName());
  }

  @Override
  public <T extends DomElement> T createMockCopy(final boolean physical) {
    throw new UnsupportedOperationException("Method createMockCopy is not yet implemented in " + getClass().getName());
  }

  @Override
  public <T extends DomElement> T createStableCopy() {
    throw new UnsupportedOperationException("Method createStableCopy is not yet implemented in " + getClass().getName());
  }

  @Override
  @Nullable
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    throw new UnsupportedOperationException("Method getAnnotation is not yet implemented in " + getClass().getName());
  }

  @Override
  public long getModificationCount() {
    return myModCount;
  }

  public void incModificationCount() {
    myModCount++;
  }
}
