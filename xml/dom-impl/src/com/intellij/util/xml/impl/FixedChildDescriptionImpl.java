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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class FixedChildDescriptionImpl extends DomChildDescriptionImpl implements DomFixedChildDescription {
  private final Collection<JavaMethod>[] myGetterMethods;
  private final int myCount;

  public FixedChildDescriptionImpl(final XmlName tagName, final Type type, final int count, final Collection<JavaMethod>[] getterMethods) {
    super(tagName, type);
    assert getterMethods.length == count || getterMethods == ArrayUtil.EMPTY_COLLECTION_ARRAY;
    myCount = count;
    myGetterMethods = getterMethods;
  }

  @Override
  public JavaMethod getGetterMethod(int index) {
    if (myGetterMethods.length == 0) return null;

    final Collection<JavaMethod> methods = myGetterMethods[index];
    return methods == null || methods.isEmpty() ? null : methods.iterator().next();
  }

  @Override
  @Nullable
  public <T extends Annotation> T getAnnotation(int index, Class<? extends T> annotationClass) {
    final JavaMethod method = getGetterMethod(index);
    if (method != null) {
      final T annotation = method.getAnnotation(annotationClass);
      if (annotation != null) return annotation;
    }

    final Type elemType = getType();
    if (elemType instanceof AnnotatedElement) {
      T annotation = ((AnnotatedElement)elemType).getAnnotation(annotationClass);
      if (annotation != null) return annotation;
    }

    return super.getAnnotation(annotationClass);
  }

  @Override
  public int getCount() {
    return myCount;
  }

  @Override
  @NotNull
  public List<? extends DomElement> getValues(@NotNull final DomElement element) {
    final List<DomElement> result = new SmartList<>();
    final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(element);
    if (handler != null) {
      for (int i = 0; i < myCount; i++) {
        result.add(handler.getFixedChild(Pair.create(this, i)).getProxy());
      }
    }
    else {
      for (Collection<JavaMethod> methods : myGetterMethods) {
        if (methods != null && !methods.isEmpty()) {
          result.add((DomElement)methods.iterator().next().invoke(element, ArrayUtil.EMPTY_OBJECT_ARRAY));
        }
      }
    }
    return result;
  }

  @Override
  @NotNull
  public String getCommonPresentableName(@NotNull DomNameStrategy strategy) {
    return StringUtil.capitalizeWords(strategy.splitIntoWords(getXmlElementName()), true);
  }

  @Override
  @Nullable
  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return getAnnotation(0, annotationClass);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final FixedChildDescriptionImpl that = (FixedChildDescriptionImpl)o;

    if (myCount != that.myCount) return false;
    if (!Arrays.equals(myGetterMethods, that.myGetterMethods)) return false;

    return true;
  }

  public String toString() {
    return getXmlElementName() + " " + getGetterMethod(0) + " " + getType();
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + myCount;
    return result;
  }
}
