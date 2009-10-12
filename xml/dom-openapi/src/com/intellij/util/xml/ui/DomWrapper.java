/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
public abstract class DomWrapper<T> {

  @NotNull
  public abstract DomElement getExistingDomElement();

  @Nullable
  public abstract DomElement getWrappedElement();

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
