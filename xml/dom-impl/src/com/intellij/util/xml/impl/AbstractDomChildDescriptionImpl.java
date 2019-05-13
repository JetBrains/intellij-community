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

import com.intellij.ide.presentation.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.references.PomService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomExtensionImpl;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public abstract class AbstractDomChildDescriptionImpl implements AbstractDomChildrenDescription, Comparable<AbstractDomChildDescriptionImpl> {
  private final Type myType;
  private Map<Class, Annotation> myCustomAnnotations;
  @Nullable private Map myUserMap;
  private volatile Ref<ElementPresentationTemplate> myPresentationTemplate = null;

  @Nullable
  private ElementPresentationTemplate calcPresentationTemplate() {
    Class clazz = ReflectionUtil.getRawType(getType());
    Presentation presentation = DomApplicationComponent.getInstance().getInvocationCache(clazz).getClassAnnotation(Presentation.class);
    return presentation == null ? null : new ElementPresentationTemplateImpl(presentation, clazz);
  }

  protected AbstractDomChildDescriptionImpl(final Type type) {
    myType = type;
  }

  public final void addCustomAnnotation(@NotNull Annotation annotation) {
    if (myCustomAnnotations == null) myCustomAnnotations = new THashMap<>();
    myCustomAnnotations.put(annotation.annotationType(), annotation);
  }

  private volatile Boolean myStubbed;

  private boolean calcStubbed() {
    return myType instanceof Class && DomReflectionUtil.findAnnotationDFS((Class)myType, Stubbed.class) != null ||
           getAnnotation(Stubbed.class) != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractDomChildDescriptionImpl that = (AbstractDomChildDescriptionImpl)o;

    if (myCustomAnnotations != null ? !myCustomAnnotations.equals(that.myCustomAnnotations) : that.myCustomAnnotations != null)
      return false;
    if (!getType().equals(that.getType())) return false;
    if (myUserMap != null ? !myUserMap.equals(that.myUserMap) : that.myUserMap != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = getType().hashCode();
    result = 31 * result + (myCustomAnnotations != null ? myCustomAnnotations.hashCode() : 0);
    result = 31 * result + (myUserMap != null ? myUserMap.hashCode() : 0);
    return result;
  }

  public void setUserMap(final Map userMap) {
    myUserMap = userMap;
  }

  @Override
  @Nullable
  public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
    return myCustomAnnotations == null ? null : (T)myCustomAnnotations.get(annotationClass);
  }

  @Override
  public <T> T getUserData(final Key<T> key) {
    return myUserMap == null ? null : (T)myUserMap.get(key);
  }

  @Override
  @NotNull
  public final List<? extends DomElement> getStableValues(@NotNull final DomElement parent) {
    final List<? extends DomElement> list = getValues(parent);
    final ArrayList<DomElement> result = new ArrayList<>(list.size());
    final DomManager domManager = parent.getManager();
    for (int i = 0; i < list.size(); i++) {
      final int i1 = i;
      result.add(domManager.createStableValue((Factory<DomElement>)() -> {
        if (!parent.isValid()) return null;

        final List<? extends DomElement> domElements = getValues(parent);
        return domElements.size() > i1 ? domElements.get(i1) : null;
      }));
    }
    return result;
  }


  @Override
  @NotNull
  public final Type getType() {
    return myType;
  }

  @Override
  @NotNull
  public DomNameStrategy getDomNameStrategy(@NotNull DomElement parent) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(ReflectionUtil.getRawType(getType()), false);
    return strategy == null ? parent.getNameStrategy() : strategy;
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
  @Nullable
  public ElementPresentationTemplate getPresentationTemplate() {
    Ref<ElementPresentationTemplate> ref = myPresentationTemplate;
    if (ref == null) {
      myPresentationTemplate = ref = Ref.create(calcPresentationTemplate());
    }
    return ref.get();
  }

  @Override
  @Nullable
  public PsiElement getDeclaration(final Project project) {
    DomElement domDeclaration = getDomDeclaration();
    if (domDeclaration != null) {
      final DomTarget target = DomTarget.getTarget(domDeclaration);
      if (target != null) {
        return PomService.convertToPsi(target);
      }
      return domDeclaration.getXmlElement();
    }
    final DomAnchor anchor = getUserData(DomExtensionImpl.KEY_DOM_DECLARATION);
    if (anchor != null) {
      return anchor.getContainingFile();
    }
    final SmartPsiElementPointer<?> pointer = getUserData(DomExtensionImpl.DECLARING_ELEMENT_KEY);
    if (pointer != null) {
      final PsiElement element = pointer.getElement();
      if (element != null) {
        return element;
      }
    }

    return PomService.convertToPsi(project, this);
  }

  @Override
  public DomElement getDomDeclaration() {
    final DomAnchor anchor = getUserData(DomExtensionImpl.KEY_DOM_DECLARATION);
    if (anchor != null) {
      return anchor.retrieveDomElement();
    }
    return null;
  }

  @Override
  public boolean isStubbed() {
    Boolean stubbed = myStubbed;
    if (stubbed == null) {
      myStubbed = stubbed = calcStubbed();
    }
    return stubbed;
  }
}
