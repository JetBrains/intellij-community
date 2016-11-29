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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class CollectionChildDescriptionImpl extends DomChildDescriptionImpl implements DomCollectionChildDescription, AbstractCollectionChildDescription {
  private final Collection<JavaMethod> myGetterMethods;

  public CollectionChildDescriptionImpl(final XmlName tagName, final Type type, final Collection<JavaMethod> getterMethods) {
    super(tagName, type);
    myGetterMethods = getterMethods;
  }

  @Override
  public String toString() {
    return "CollectionChildDescription:" + getXmlName();
  }

  List<XmlTag> getCollectionSubTags(DomInvocationHandler<?,?> handler, @NotNull XmlTag tag) {
    return DomImplUtil.findSubTags(tag, handler.createEvaluatedXmlName(getXmlName()), handler.getFile());
  }

  @Override
  public DomElement addValue(@NotNull DomElement element) {
    assert element.getGenericInfo().getCollectionChildrenDescriptions().contains(this);
    return addChild(element, getType(), Integer.MAX_VALUE);
  }

  private DomElement addChild(final DomElement element, final Type type, final int index) {
    try {
      final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(element);
      assert handler != null;
      return handler.addCollectionChild(this, type, index);
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public DomElement addValue(@NotNull DomElement element, int index) {
    return addChild(element, getType(), index);
  }

  @Override
  public DomElement addValue(@NotNull DomElement parent, Type type) {
    return addValue(parent, type, Integer.MAX_VALUE);
  }

  @Override
  public final DomElement addValue(@NotNull DomElement parent, Type type, int index) {
    return addChild(parent, type, index);
  }

  @Override
  @Nullable
  public final JavaMethod getGetterMethod() {
    final Collection<JavaMethod> methods = myGetterMethods;
    return methods.isEmpty() ? null : methods.iterator().next();
  }

  @Override
  @NotNull
  public List<? extends DomElement> getValues(@NotNull final DomElement element) {
    final DomInvocationHandler<?,?> handler = DomManagerImpl.getDomInvocationHandler(element);
    if (handler != null) {
      return handler.getCollectionChildren(this);
    }
    final JavaMethod getterMethod = getGetterMethod();
    if (getterMethod == null) {
      final Collection<DomElement> collection = ModelMergerUtil.getFilteredImplementations(element);
      return ContainerUtil.concat(collection, (Function<DomElement, Collection<? extends DomElement>>)domElement -> {
        final DomInvocationHandler<?,?> handler1 = DomManagerImpl.getDomInvocationHandler(domElement);
        assert handler1 != null : domElement;
        return handler1.getCollectionChildren(this);
      });
    }
    //noinspection unchecked
    return (List<? extends DomElement>)getterMethod.invoke(element, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  @Override
  @NotNull
  public String getCommonPresentableName(@NotNull DomNameStrategy strategy) {
    String words = strategy.splitIntoWords(getXmlElementName());
    return StringUtil.capitalizeWords(words.endsWith("es") ? words: StringUtil.pluralize(words), true);
  }

  @Override
  @Nullable
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    final JavaMethod method = getGetterMethod();
    if (method != null) {
      final T annotation = method.getAnnotation(annotationClass);
      if (annotation != null) return annotation;
    }

    final Type elemType = getType();
    return elemType instanceof AnnotatedElement ? ((AnnotatedElement)elemType).getAnnotation(annotationClass) : super.getAnnotation(annotationClass);
  }

  @Override
  public List<XmlTag> getSubTags(final DomInvocationHandler handler, final XmlTag[] subTags, final XmlFile file) {
    return DomImplUtil.findSubTags(subTags, handler.createEvaluatedXmlName(getXmlName()), file);
  }

  @Override
  public EvaluatedXmlName createEvaluatedXmlName(final DomInvocationHandler parent, final XmlTag childTag) {
    return parent.createEvaluatedXmlName(getXmlName());
  }
}
