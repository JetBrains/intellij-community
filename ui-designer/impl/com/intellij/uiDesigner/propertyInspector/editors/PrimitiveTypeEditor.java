/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.editors;

import java.lang.reflect.Method;

/**
 * @author yole
 */
public final class PrimitiveTypeEditor<T> extends AbstractTextFieldEditor<T> {
  private Class<T> myClass;

  public PrimitiveTypeEditor(final Class<T> aClass) {
    myClass = aClass;
  }

  public T getValue() throws Exception {
    final Method method = myClass.getMethod("valueOf", String.class);
    //noinspection unchecked
    return (T) method.invoke(null, myTf.getText());
  }
}