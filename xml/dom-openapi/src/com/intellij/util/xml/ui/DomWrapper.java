// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

public abstract class DomWrapper<T> {

  public abstract @NotNull DomElement getExistingDomElement();

  public abstract @Nullable DomElement getWrappedElement();

  public abstract void setValue(T value) throws IllegalAccessException, InvocationTargetException;
  public abstract T getValue() throws IllegalAccessException, InvocationTargetException;

  public boolean isValid() {
    return getExistingDomElement().isValid();
  }

  public Project getProject() {
    return getExistingDomElement().getManager().getProject();
  }

  public GlobalSearchScope getResolveScope() {
    return getExistingDomElement().getResolveScope();
  }

  public XmlFile getFile() {
    return DomUtil.getFile(getExistingDomElement());
  }
}
