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
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
@Presentation(typeName = "Attribute")
public class AttributeChildDescriptionImpl extends DomChildDescriptionImpl implements DomAttributeChildDescription<Void> {
  private final JavaMethod myGetterMethod;

  protected AttributeChildDescriptionImpl(final XmlName attributeName, @NotNull final JavaMethod getter) {
    super(attributeName, getter.getGenericReturnType());
    myGetterMethod = getter;
  }

  public AttributeChildDescriptionImpl(final XmlName attributeName, @NotNull Type type) {
    super(attributeName, type);
    myGetterMethod = null;
  }

  @Override
  @NotNull
  public DomNameStrategy getDomNameStrategy(@NotNull DomElement parent) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(ReflectionUtil.getRawType(getType()), true);
    return strategy == null ? parent.getNameStrategy() : strategy;
  }


  @Override
  public final JavaMethod getGetterMethod() {
    return myGetterMethod;
  }

  @Override
  public String toString() {
    return "Attribute:" + getXmlName();
  }

  @Override
  @Nullable
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    final JavaMethod method = getGetterMethod();
    return method == null ? super.getAnnotation(annotationClass) : method.getAnnotation(annotationClass);
  }

  @Override
  @NotNull
  public List<? extends DomElement> getValues(@NotNull DomElement parent) {
    return Arrays.asList(getDomAttributeValue(parent));
  }

  @Override
  @NotNull
  public String getCommonPresentableName(@NotNull DomNameStrategy strategy) {
    throw new UnsupportedOperationException("Method getCommonPresentableName is not yet implemented in " + getClass().getName());
  }

  @Override
  public GenericAttributeValue getDomAttributeValue(DomElement parent) {
    final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(parent);
    if (handler != null) {
      return getDomAttributeValue(handler);
    }
    return (GenericAttributeValue)myGetterMethod.invoke(parent, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public GenericAttributeValue getDomAttributeValue(final DomInvocationHandler handler) {
    return (GenericAttributeValue)handler.getAttributeChild(this).getProxy();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final AttributeChildDescriptionImpl that = (AttributeChildDescriptionImpl)o;

    if (myGetterMethod != null ? !myGetterMethod.equals(that.myGetterMethod) : that.myGetterMethod != null) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + (myGetterMethod != null ? myGetterMethod.hashCode() : 0);
    return result;
  }
}
