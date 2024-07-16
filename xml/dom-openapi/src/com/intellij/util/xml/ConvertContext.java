// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ConvertContext {

  public abstract @NotNull DomElement getInvocationElement();

  public abstract @Nullable XmlTag getTag();

  public abstract @Nullable XmlElement getXmlElement();

  public @Nullable XmlElement getReferenceXmlElement() {
    final XmlElement element = getXmlElement();
    if (element instanceof XmlTag) {
      return element;
    }
    if (element instanceof XmlAttribute) {
      return ((XmlAttribute)element).getValueElement();
    }
    return null;
  }

  public abstract @NotNull XmlFile getFile();

  public abstract @Nullable Module getModule();

  public abstract @Nullable GlobalSearchScope getSearchScope();
  
  public PsiManager getPsiManager() {
    return PsiManager.getInstance(getProject());
  }

  public Project getProject() {
    return getInvocationElement().getManager().getProject();
  }
}
