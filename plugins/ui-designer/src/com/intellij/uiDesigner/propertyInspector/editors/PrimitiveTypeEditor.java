
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
