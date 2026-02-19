// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public final class IntroIntProperty extends IntrospectedProperty<Integer> {
  private PropertyRenderer<Integer> myRenderer;
  private PropertyEditor<Integer> myEditor;

  public IntroIntProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    this(name, readMethod, writeMethod, null, null, storeAsClient);
  }

  public IntroIntProperty(final String name,
                          final Method readMethod,
                          final Method writeMethod,
                          final PropertyRenderer<Integer> renderer,
                          final PropertyEditor<Integer> editor,
                          final boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
    myRenderer = renderer;
    myEditor = editor;
  }

  @Override
  public @NotNull PropertyRenderer<Integer> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new LabelPropertyRenderer<>();
    }
    return myRenderer;
  }

  @Override
  public PropertyEditor<Integer> getEditor() {
    if (myEditor == null) {
      myEditor = new IntEditor(Integer.MIN_VALUE);
    }
    return myEditor;
  }
}
