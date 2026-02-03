// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;

public class DomStringWrapper extends DomWrapper<String>{
  private final GenericDomValue myDomElement;

  public DomStringWrapper(final GenericDomValue domElement) {
    myDomElement = domElement;
  }

  @Override
  public @NotNull DomElement getExistingDomElement() {
    return myDomElement;
  }

  @Override
  public DomElement getWrappedElement() {
    return myDomElement;
  }

  @Override
  public void setValue(final String value) throws IllegalAccessException, InvocationTargetException {
    myDomElement.setStringValue(value);
  }

  @Override
  public String getValue() throws IllegalAccessException, InvocationTargetException {
    return myDomElement.isValid() ? myDomElement.getStringValue() : null;
  }

}
