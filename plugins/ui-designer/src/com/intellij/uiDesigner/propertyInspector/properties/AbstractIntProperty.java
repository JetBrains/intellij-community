// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class AbstractIntProperty<T extends RadComponent> extends Property<T, Integer> {
  private final int myDefaultValue;
  private final LabelPropertyRenderer<Integer> myRenderer = new LabelPropertyRenderer<>();
  private final IntEditor myEditor;

  protected AbstractIntProperty(Property parent, @NotNull @NonNls String name, int defaultValue) {
    super(parent, name);
    myDefaultValue = defaultValue;
    myEditor = new IntEditor(defaultValue);
  }

  @Override
  public @NotNull PropertyRenderer<Integer> getRenderer() {
    return myRenderer;
  }

  @Override
  public @Nullable PropertyEditor<Integer> getEditor() {
    return myEditor;
  }

  @Override public boolean isModified(final T component) {
    Integer intValue = getValue(component);
    return intValue != null && intValue.intValue() != getDefaultValue(component);
  }

  @Override public void resetValue(T component) throws Exception {
    setValue(component, getDefaultValue(component));
  }

  protected int getDefaultValue(final T component) {
    return myDefaultValue;
  }
}
