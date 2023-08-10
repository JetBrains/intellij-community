// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.EnumEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;


public class IntroEnumProperty extends IntrospectedProperty<Enum> {
  private final Class myEnumClass;
  private LabelPropertyRenderer<Enum> myRenderer;
  private EnumEditor myEditor;

  public IntroEnumProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient,
                           Class enumClass) {
    super(name, readMethod, writeMethod, storeAsClient);
    myEnumClass = enumClass;
  }

  @Override
  @NotNull
  public PropertyRenderer<Enum> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new LabelPropertyRenderer<>();
    }
    return myRenderer;
  }

  @Override
  public PropertyEditor<Enum> getEditor() {
    if (myEditor == null) {
      myEditor = new EnumEditor(myEnumClass);
    }
    return myEditor;
  }
}
