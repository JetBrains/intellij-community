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
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class ConvertContext {

  @NotNull
  public abstract DomElement getInvocationElement();

  @Nullable
  public abstract XmlTag getTag();

  @Nullable
  public abstract XmlElement getXmlElement();

  @Nullable
  public XmlElement getReferenceXmlElement() {
    final XmlElement element = getXmlElement();
    if (element instanceof XmlTag) {
      return element;
    }
    if (element instanceof XmlAttribute) {
      return ((XmlAttribute)element).getValueElement();
    }
    return null;
  }

  @NotNull
  public abstract XmlFile getFile();

  @Nullable
  public abstract Module getModule();
  
  public abstract PsiManager getPsiManager();

  public Project getProject() {
    return getPsiManager().getProject();
  }
}
