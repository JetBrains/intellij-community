/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.EnumEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroEnumProperty extends IntrospectedProperty<Enum> {
  private Class myEnumClass;
  private LabelPropertyRenderer<Enum> myRenderer;
  private EnumEditor myEditor;

  public IntroEnumProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient,
                           Class enumClass) {
    super(name, readMethod, writeMethod, storeAsClient);
    myEnumClass = enumClass;
  }

  @NotNull
  public PropertyRenderer<Enum> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new LabelPropertyRenderer<Enum>();
    }
    return myRenderer;
  }

  public PropertyEditor<Enum> getEditor() {
    if (myEditor == null) {
      myEditor = new EnumEditor(myEnumClass);
    }
    return myEditor;
  }
}
