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
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Key;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.ReflectionUtil;
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

  protected AbstractDomChildDescriptionImpl(final Type type) {
    myType = type;
  }

  public final void addCustomAnnotation(@NotNull Annotation annotation) {
    if (myCustomAnnotations == null) myCustomAnnotations = new THashMap<Class, Annotation>();
    myCustomAnnotations.put(annotation.annotationType(), annotation);
  }

  public void setUserMap(final Map userMap) {
    myUserMap = userMap;
  }

  @Nullable
  public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
    return myCustomAnnotations == null ? null : (T)myCustomAnnotations.get(annotationClass);
  }

  public <T> T getUserData(final Key<T> key) {
    return myUserMap == null ? null : (T)myUserMap.get(key);
  }

  @NotNull
  public final List<? extends DomElement> getStableValues(@NotNull final DomElement parent) {
    final List<? extends DomElement> list = getValues(parent);
    final ArrayList<DomElement> result = new ArrayList<DomElement>(list.size());
    final DomManager domManager = parent.getManager();
    for (int i = 0; i < list.size(); i++) {
      final int i1 = i;
      result.add(domManager.createStableValue(new Factory<DomElement>() {
        @Nullable
        public DomElement create() {
          if (!parent.isValid()) return null;

          final List<? extends DomElement> domElements = getValues(parent);
          return domElements.size() > i1 ? domElements.get(i1) : null;
        }
      }));
    }
    return result;
  }


  @NotNull
  public final Type getType() {
    return myType;
  }

  @NotNull
  public DomNameStrategy getDomNameStrategy(@NotNull DomElement parent) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(ReflectionUtil.getRawType(myType), false);
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
}
