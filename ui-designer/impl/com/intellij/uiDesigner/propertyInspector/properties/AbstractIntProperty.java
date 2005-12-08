package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.IntRenderer;
import com.intellij.uiDesigner.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class AbstractIntProperty extends Property {
  private int myDefaultValue;
  private IntRenderer myRenderer = new IntRenderer();
  private IntEditor myEditor;

  protected AbstractIntProperty(Property parent, @NotNull @NonNls String name, int defaultValue) {
    super(parent, name);
    myDefaultValue = defaultValue;
    myEditor = new IntEditor(defaultValue);
  }

  @NotNull public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Nullable public PropertyEditor getEditor() {
    return myEditor;
  }

  @Override public boolean isModified(final RadComponent component) {
    Integer intValue = (Integer) getValue(component);
    return intValue != null && intValue.intValue() != myDefaultValue;
  }
}
