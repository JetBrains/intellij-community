// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.ide.presentation.Presentation;
import com.intellij.serialization.ClassUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Presentation(typeName = "Attribute")
public class AttributeChildDescriptionImpl extends DomChildDescriptionImpl implements DomAttributeChildDescription<Void> {
  private final JavaMethod myGetterMethod;

  protected AttributeChildDescriptionImpl(final XmlName attributeName, final @NotNull JavaMethod getter) {
    super(attributeName, getter.getGenericReturnType());
    myGetterMethod = getter;
  }

  public AttributeChildDescriptionImpl(final XmlName attributeName, @NotNull Type type) {
    super(attributeName, type);
    myGetterMethod = null;
  }

  @Override
  public @NotNull DomNameStrategy getDomNameStrategy(@NotNull DomElement parent) {
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(ClassUtil.getRawType(getType()), true);
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
  public @Nullable <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    final JavaMethod method = getGetterMethod();
    return method == null ? super.getAnnotation(annotationClass) : method.getAnnotation(annotationClass);
  }

  @Override
  public @NotNull List<? extends DomElement> getValues(@NotNull DomElement parent) {
    return Collections.singletonList(getDomAttributeValue(parent));
  }

  @Override
  public @NotNull String getCommonPresentableName(@NotNull DomNameStrategy strategy) {
    throw new UnsupportedOperationException("Method getCommonPresentableName is not yet implemented in " + getClass().getName());
  }

  @Override
  public GenericAttributeValue getDomAttributeValue(DomElement parent) {
    final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(parent);
    if (handler != null) {
      return getDomAttributeValue(handler);
    }
    return (GenericAttributeValue)myGetterMethod.invoke(parent, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
  }

  public GenericAttributeValue getDomAttributeValue(final DomInvocationHandler handler) {
    return (GenericAttributeValue)handler.getAttributeChild(this).getProxy();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final AttributeChildDescriptionImpl that = (AttributeChildDescriptionImpl)o;

    return Objects.equals(myGetterMethod, that.myGetterMethod);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + (myGetterMethod != null ? myGetterMethod.hashCode() : 0);
    return result;
  }
}
