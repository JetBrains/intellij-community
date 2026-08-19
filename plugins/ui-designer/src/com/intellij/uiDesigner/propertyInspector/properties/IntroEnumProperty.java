// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.lw.EnumDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.EnumEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;


public class IntroEnumProperty extends IntrospectedProperty<Enum<?>> {
  private final Class<?> myEnumClass;
  private LabelPropertyRenderer<Enum<?>> myRenderer;
  private EnumEditor myEditor;

  public IntroEnumProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient,
                           Class<?> enumClass) {
    super(name, readMethod, writeMethod, storeAsClient);
    myEnumClass = enumClass;
  }

  /**
   * The form file only names the constant. The enum class has to be the one the component class was loaded with,
   * which is {@link #myEnumClass} - a class loaded by whoever read the form would be a different class, and the
   * setter of the component would reject it.
   */
  @Override
  public Enum<?> fromLwValue(final Object lwValue) {
    if (!(lwValue instanceof EnumDescriptor descriptor)) {
      return (Enum<?>)lwValue;
    }
    Object[] constants = myEnumClass.getEnumConstants();
    if (constants != null) {
      for (Object constant : constants) {
        Enum<?> value = (Enum<?>)constant;
        if (value.name().equals(descriptor.getConstantName())) {
          return value;
        }
      }
    }
    // the constant was renamed or removed since the form was written - skip the property, as reading it did before
    return null;
  }

  @Override
  public @NotNull PropertyRenderer<Enum<?>> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new LabelPropertyRenderer<>();
    }
    return myRenderer;
  }

  @Override
  public PropertyEditor<Enum<?>> getEditor() {
    if (myEditor == null) {
      myEditor = new EnumEditor(myEnumClass);
    }
    return myEditor;
  }
}
