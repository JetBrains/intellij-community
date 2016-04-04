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

/**
 * @author mike
 */
public class DomElementXmlDescriptor extends AbstractDomChildrenDescriptor implements XmlHighlightingAwareElementDescriptor {
  private final DomChildrenDescription myChildrenDescription;

  public DomElementXmlDescriptor(@NotNull final DomElement domElement) {
    super(domElement.getManager());
    myChildrenDescription = new MyRootDomChildrenDescription(domElement);
  }

  public DomElementXmlDescriptor(@NotNull final DomChildrenDescription childrenDescription, final DomManager manager) {
    super(manager);
    myChildrenDescription = childrenDescription;
  }

  @Override
  public String getDefaultName() {
    return myChildrenDescription.getXmlElementName();
  }

  @Override
  @Nullable
  public PsiElement getDeclaration() {
    return myChildrenDescription.getDeclaration(myManager.getProject());
  }

  @Override
  @NonNls
  public String getName(final PsiElement context) {
    final String name = getDefaultName();
    if (context instanceof XmlTag) {
      XmlTag tag = (XmlTag)context;
      final PsiFile file = tag.getContainingFile();
      DomElement element = myManager.getDomElement(tag);
      if (element == null && tag.getParentTag() != null) {
        element = myManager.getDomElement(tag.getParentTag());
      }
      if (element != null && file instanceof XmlFile && !(myChildrenDescription instanceof MyRootDomChildrenDescription)) {
        final String namespace = DomService.getInstance().getEvaluatedXmlName(element).evaluateChildName(myChildrenDescription.getXmlName()).getNamespace(tag, (XmlFile)file);
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

  @Presentation(typeName = "Root tag")
  private static class MyRootDomChildrenDescription implements DomChildrenDescription {
    private final DomElement myDomElement;

    public MyRootDomChildrenDescription(final DomElement domElement) {
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
    public void navigate(boolean requestFocus) {
    }

    @Override
    public boolean canNavigate() {
      return false;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    @NotNull
    public XmlName getXmlName() {
      throw new UnsupportedOperationException("Method getXmlName not implemented in " + getClass());
    }

    @Override
    @NotNull
    public String getXmlElementName() {
      return myDomElement.getXmlElementName();
    }

    @Override
    @NotNull
      public String getCommonPresentableName(@NotNull final DomNameStrategy strategy) {
      throw new UnsupportedOperationException("Method getCommonPresentableName not implemented in " + getClass());
    }

    @Override
    @NotNull
      public String getCommonPresentableName(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getCommonPresentableName not implemented in " + getClass());
    }

    @Override
    @NotNull
      public List<? extends DomElement> getValues(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getValues not implemented in " + getClass());
    }

    @Override
    @NotNull
      public List<? extends DomElement> getStableValues(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getStableValues not implemented in " + getClass());
    }

    @Override
    @NotNull
      public Type getType() {
      throw new UnsupportedOperationException("Method getType not implemented in " + getClass());
    }

    @Override
    @NotNull
      public DomNameStrategy getDomNameStrategy(@NotNull final DomElement parent) {
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
    @Nullable
      public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
          throw new UnsupportedOperationException("Method getAnnotation not implemented in " + getClass());
        }

    @Override
    @Nullable
    public PsiElement getDeclaration(final Project project) {
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
