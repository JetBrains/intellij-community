// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl.dom;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightingAwareElementDescriptor;
import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

public class DomElementXmlDescriptor extends AbstractDomChildrenDescriptor implements XmlHighlightingAwareElementDescriptor {
  private final DomChildrenDescription myChildrenDescription;

  public DomElementXmlDescriptor(final @NotNull DomElement domElement) {
    super(domElement.getManager());
    myChildrenDescription = new MyRootDomChildrenDescription(domElement);
  }

  public DomElementXmlDescriptor(final @NotNull DomChildrenDescription childrenDescription, final DomManager manager) {
    super(manager);
    myChildrenDescription = childrenDescription;
  }

  @Override
  public String getDefaultName() {
    return myChildrenDescription.getXmlElementName();
  }

  @Override
  public @Nullable PsiElement getDeclaration() {
    return myChildrenDescription.getDeclaration(myManager.getProject());
  }

  @Override
  public @NonNls String getName(final PsiElement context) {
    final String name = getDefaultName();
    if (context instanceof XmlTag tag) {
      final PsiFile file = tag.getContainingFile();
      DomElement element = myManager.getDomElement(tag);
      if (element == null && tag.getParentTag() != null) {
        element = myManager.getDomElement(tag.getParentTag());
      }
      if (element != null && file instanceof XmlFile && !(myChildrenDescription instanceof MyRootDomChildrenDescription)) {
        final String namespace = DomService.getInstance().getEvaluatedXmlName(element)
          .evaluateChildName(myChildrenDescription.getXmlName())
          .getNamespace(tag, (XmlFile)file);
        if (!tag.getNamespaceByPrefix("").equals(namespace)) {
          final String s = tag.getPrefixByNamespace(namespace);
          if (StringUtil.isNotEmpty(s)) {
            return s + ":" + name;
          }
        }
      }
    }

    return name;
  }

  @Override
  public boolean shouldCheckRequiredAttributes() {
    return false;
  }

  @Presentation(typeName = "Root Tag")
  private static class MyRootDomChildrenDescription implements DomChildrenDescription {
    private final DomElement myDomElement;

    MyRootDomChildrenDescription(final DomElement domElement) {
      myDomElement = domElement;
    }

    @Override
    public String getName() {
      return getXmlElementName();
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public @NotNull XmlName getXmlName() {
      throw new UnsupportedOperationException("Method getXmlName not implemented in " + getClass());
    }

    @Override
    public @NotNull String getXmlElementName() {
      return myDomElement.getXmlElementName();
    }

    @Override
    public @NotNull String getCommonPresentableName(final @NotNull DomNameStrategy strategy) {
      throw new UnsupportedOperationException("Method getCommonPresentableName not implemented in " + getClass());
    }

    @Override
    public @NotNull String getCommonPresentableName(final @NotNull DomElement parent) {
      throw new UnsupportedOperationException("Method getCommonPresentableName not implemented in " + getClass());
    }

    @Override
    public @NotNull List<? extends DomElement> getValues(final @NotNull DomElement parent) {
      throw new UnsupportedOperationException("Method getValues not implemented in " + getClass());
    }

    @Override
    public @NotNull List<? extends DomElement> getStableValues(final @NotNull DomElement parent) {
      throw new UnsupportedOperationException("Method getStableValues not implemented in " + getClass());
    }

    @Override
    public @NotNull Type getType() {
      throw new UnsupportedOperationException("Method getType not implemented in " + getClass());
    }

    @Override
    public @NotNull DomNameStrategy getDomNameStrategy(final @NotNull DomElement parent) {
      throw new UnsupportedOperationException("Method getDomNameStrategy not implemented in " + getClass());
    }

    @Override
    public <T> T getUserData(final Key<T> key) {
      return null;
    }

    @Override
    public ElementPresentationTemplate getPresentationTemplate() {
      return null;
    }

    @Override
    public @Nullable <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
      throw new UnsupportedOperationException("Method getAnnotation not implemented in " + getClass());
    }

    @Override
    public @Nullable PsiElement getDeclaration(final Project project) {
      return PomService.convertToPsi(project, this);
    }

    @Override
    public DomElement getDomDeclaration() {
      return myDomElement;
    }

    @Override
    public boolean isStubbed() {
      return false;
    }
  }
}
