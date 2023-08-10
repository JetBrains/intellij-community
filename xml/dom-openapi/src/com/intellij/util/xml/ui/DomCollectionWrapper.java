// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

import com.intellij.serialization.ClassUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public final class DomCollectionWrapper<T> extends DomWrapper<T> {
  private final DomElement myDomElement;
  private final DomCollectionChildDescription myChildDescription;
  private final Method mySetter;
  private final Method myGetter;

  public DomCollectionWrapper(DomElement domElement, DomCollectionChildDescription childDescription) {
    this(domElement, childDescription,
         DomUIFactory.findMethod(ClassUtil.getRawType(childDescription.getType()), "setValue"),
         DomUIFactory.findMethod(ClassUtil.getRawType(childDescription.getType()), "getValue"));
  }

  public DomCollectionWrapper(DomElement domElement,
                              DomCollectionChildDescription childDescription,
                              Method setter,
                              Method getter) {
    myDomElement = domElement;
    myChildDescription = childDescription;
    mySetter = setter;
    myGetter = getter;
  }

  @Override
  public @NotNull DomElement getExistingDomElement() {
    return myDomElement;
  }

  @Override
  public DomElement getWrappedElement() {
    List<? extends DomElement> list = myChildDescription.getValues(myDomElement);
    return list.isEmpty() ? null : list.get(0);
  }

  @Override
  public void setValue(final T value) throws IllegalAccessException, InvocationTargetException {
    List<? extends DomElement> list = myChildDescription.getValues(myDomElement);
    DomElement domElement = list.isEmpty() ? myChildDescription.addValue(myDomElement) : list.get(0);
    mySetter.invoke(domElement, value);
  }

  @SuppressWarnings("unchecked")
  @Override
  public T getValue() throws IllegalAccessException, InvocationTargetException {
    if (!myDomElement.isValid()) {
      return null;
    }

    List<? extends DomElement> list = myChildDescription.getValues(myDomElement);
    return list.isEmpty() ? null : (T)myGetter.invoke(list.get(0));
  }
}
