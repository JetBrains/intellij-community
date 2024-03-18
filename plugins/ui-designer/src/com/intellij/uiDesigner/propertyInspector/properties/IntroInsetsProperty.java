// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.InsetsEditor;
import com.intellij.uiDesigner.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.InsetsPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.lang.reflect.Method;

public final class IntroInsetsProperty extends IntrospectedProperty<Insets> {
  private final Property[] myChildren;
  private final InsetsPropertyRenderer myRenderer;
  private final IntRegexEditor<Insets> myEditor;

  public IntroInsetsProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient){
    super(name, readMethod, writeMethod, storeAsClient);
    myChildren=new Property[]{
      new IntFieldProperty(this, "top", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "left", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "bottom", 0, new Insets(0, 0, 0, 0)),
      new IntFieldProperty(this, "right", 0, new Insets(0, 0, 0, 0)),
    };
    myRenderer=new InsetsPropertyRenderer();
    myEditor = new InsetsEditor(myRenderer);
  }

  @Override
  public void write(final Insets value, final XmlWriter writer) {
    writer.writeInsets(value);
  }

  @Override
  public Property @NotNull [] getChildren(final RadComponent component) {
    return myChildren;
  }

  @Override
  public @NotNull PropertyRenderer<Insets> getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor<Insets> getEditor() {
    return myEditor;
  }
}
