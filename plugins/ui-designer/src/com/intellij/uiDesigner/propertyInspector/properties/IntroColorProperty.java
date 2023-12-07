// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.lw.ColorDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ColorEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.ColorRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.Method;


public class IntroColorProperty extends IntrospectedProperty<ColorDescriptor> {
  private ColorRenderer myColorRenderer = null;
  private ColorEditor myColorEditor = null;
  private static final @NonNls String CLIENT_PROPERTY_KEY_PREFIX = "IntroColorProperty_";

  public IntroColorProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
  }

  @Override
  public @NotNull PropertyRenderer<ColorDescriptor> getRenderer() {
    if (myColorRenderer == null) {
      myColorRenderer = new ColorRenderer();
    }
    return myColorRenderer;
  }

  @Override
  public @Nullable PropertyEditor<ColorDescriptor> getEditor() {
    if (myColorEditor == null) {
      myColorEditor = new ColorEditor(getName());
    }
    return myColorEditor;
  }

  @Override
  public void write(@NotNull ColorDescriptor value, XmlWriter writer) {
    writer.writeColorDescriptor(value);
  }

  @Override public ColorDescriptor getValue(final RadComponent component) {
    final ColorDescriptor colorDescriptor = (ColorDescriptor)component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
    if (colorDescriptor == null) {
      return new ColorDescriptor((Color) invokeGetter(component));
    }
    return colorDescriptor;
  }

  @Override protected void setValueImpl(final RadComponent component, final ColorDescriptor value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    if (value != null && value.isColorSet()) {
      invokeSetter(component, value.getResolvedColor());
    }
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    super.resetValue(component);
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), null);
  }
}
