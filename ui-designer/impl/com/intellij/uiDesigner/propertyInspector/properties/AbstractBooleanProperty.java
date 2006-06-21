/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class AbstractBooleanProperty<T extends RadComponent> extends Property<T, Boolean> {
  private BooleanRenderer myRenderer;
  private BooleanEditor myEditor;
  private final boolean myDefaultValue;

  protected AbstractBooleanProperty(final Property parent, @NonNls final String name, final boolean defaultValue) {
    super(parent, name);
    myDefaultValue = defaultValue;
  }

  @NotNull
  public PropertyRenderer<Boolean> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new BooleanRenderer();
    }
    return myRenderer;
  }

  public PropertyEditor<Boolean> getEditor() {
    if (myEditor == null) {
      myEditor = new BooleanEditor();
    }
    return myEditor;
  }

  @Override public boolean isModified(final T component) {
    Boolean intValue = getValue(component);
    return intValue != null && intValue.booleanValue() != getDefaultValue(component);
  }

  @Override public void resetValue(T component) throws Exception {
    setValue(component, getDefaultValue(component));
  }

  protected boolean getDefaultValue(final T component) {
    return myDefaultValue;
  }
}
