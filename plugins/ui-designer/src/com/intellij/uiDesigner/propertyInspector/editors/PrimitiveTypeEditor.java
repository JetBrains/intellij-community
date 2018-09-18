
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.editors;

import java.lang.reflect.Method;

/**
 * @author yole
 */
public final class PrimitiveTypeEditor<T> extends AbstractTextFieldEditor<T> {
  private final Class<T> myClass;

  public PrimitiveTypeEditor(final Class<T> aClass) {
    myClass = aClass;
  }

  @Override
  public T getValue() throws Exception {
    try {
      final Method method = myClass.getMethod("valueOf", String.class);
      //noinspection unchecked
      return (T) method.invoke(null, myTf.getText());
    }
    catch (NumberFormatException e) {
      throw new RuntimeException("Entered value is not a valid number of this property type");
    }
  }
}
