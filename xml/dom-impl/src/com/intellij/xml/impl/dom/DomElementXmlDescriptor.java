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
package com.intellij.xml.impl.dom;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author mike
 */
public class DomElementXmlDescriptor extends AbstractDomChildrenDescriptor {
  private final DomChildrenDescription myChildrenDescription;

  public DomElementXmlDescriptor(@NotNull final DomElement domElement) {
    super(domElement.getManager());
    myChildrenDescription = new MyRootDomChildrenDescription(domElement);
  }

  public DomElementXmlDescriptor(@NotNull final DomChildrenDescription childrenDescription, final DomManager manager) {
    super(manager);
    myChildrenDescription = childrenDescription;
  }

  public String getDefaultName() {
    return myChildrenDescription.getXmlElementName();
  }

  /**
   * @return minimal occurrence constraint value (e.g. 0 or 1), on null if not applied
   */
  @Override
  public Integer getMinOccurs() {
    return null;
  }

  /**
   * @return maximal occurrence constraint value (e.g. 1 or {@link Integer.MAX_VALUE}), on null if not applied
   */
  @Override
  public Integer getMaxOccurs() {
    return null;
  }

  @Nullable
  public PsiElement getDeclaration() {
    final DomElement declaration = myChildrenDescription.getUserData(DomExtension.KEY_DECLARATION);

    if (declaration != null) {
      final DomTarget target = DomTarget.getTarget(declaration);
      if (target != null) {
        return PomService.convertToPsi(target);
      }
      return declaration.getXmlElement();
    }

    return PomService.convertToPsi(myManager.getProject(), myChildrenDescription);
  }

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

  private static class MyRootDomChildrenDescription implements DomChildrenDescription {
    private final DomElement myDomElement;

    public MyRootDomChildrenDescription(final DomElement domElement) {
      myDomElement = domElement;
    }

    public String getName() {
      return getXmlElementName();
    }

    public boolean isValid() {
      return true;
    }

    public void navigate(boolean requestFocus) {
    }

    public boolean canNavigate() {
      return false;
    }

    public boolean canNavigateToSource() {
      return false;
    }

    @NotNull
    public XmlName getXmlName() {
      throw new UnsupportedOperationException("Method getXmlName not implemented in " + getClass());
    }

    @NotNull
    public String getXmlElementName() {
      return myDomElement.getXmlElementName();
    }

    @NotNull
      public String getCommonPresentableName(@NotNull final DomNameStrategy strategy) {
      throw new UnsupportedOperationException("Method getCommonPresentableName not implemented in " + getClass());
    }

    @NotNull
      public String getCommonPresentableName(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getCommonPresentableName not implemented in " + getClass());
    }

    @NotNull
      public List<? extends DomElement> getValues(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getValues not implemented in " + getClass());
    }

    @NotNull
      public List<? extends DomElement> getStableValues(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getStableValues not implemented in " + getClass());
    }

    @NotNull
      public Type getType() {
      throw new UnsupportedOperationException("Method getType not implemented in " + getClass());
    }

    @NotNull
      public DomNameStrategy getDomNameStrategy(@NotNull final DomElement parent) {
      throw new UnsupportedOperationException("Method getDomNameStrategy not implemented in " + getClass());
    }

    public <T> T getUserData(final Key<T> key) {
      return null;
    }

    @Nullable
      public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
          throw new UnsupportedOperationException("Method getAnnotation not implemented in " + getClass());
        }
  }

}
