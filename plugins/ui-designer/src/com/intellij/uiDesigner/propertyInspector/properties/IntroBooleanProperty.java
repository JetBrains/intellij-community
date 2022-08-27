// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

public final class IntroBooleanProperty extends IntrospectedProperty<Boolean> {
  private BooleanRenderer myRenderer;
  private BooleanEditor myEditor;

  public IntroBooleanProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
  }

  @Override
  public PropertyEditor<Boolean> getEditor() {
    if (myEditor == null) {
      myEditor = new BooleanEditor();
    }
    return myEditor;
  }

  @Override
  @NotNull
  public PropertyRenderer<Boolean> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new BooleanRenderer();
    }
    return myRenderer;
  }
}
