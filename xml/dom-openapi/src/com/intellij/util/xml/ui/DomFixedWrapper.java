// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;

public class DomFixedWrapper<T> extends DomWrapper<T>{
  private final GenericDomValue myDomElement;

  public DomFixedWrapper(final GenericDomValue<? extends T> domElement) {
    myDomElement = domElement;
  }

  @Override
  public DomElement getWrappedElement() {
    return myDomElement;
  }

  @Override
  public void setValue(final T value) throws IllegalAccessException, InvocationTargetException {
    DomUIFactory.SET_VALUE_METHOD.invoke(getWrappedElement(), value);
  }

  @Override
  public T getValue() throws IllegalAccessException, InvocationTargetException {
    final DomElement element = getWrappedElement();
    return element.isValid() ? (T)DomUIFactory.GET_VALUE_METHOD.invoke(element) : null;
  }

  @Override
  public @NotNull DomElement getExistingDomElement() {
    return myDomElement;
  }


}
