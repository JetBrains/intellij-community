package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.editors.IntEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.IntRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class AbstractIntProperty<T extends RadComponent> extends Property<T, Integer> {
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

  @Override public boolean isModified(final T component) {
    Integer intValue = getValue(component);
    return intValue != null && intValue.intValue() != myDefaultValue;
  }

  @Override public void resetValue(T component) throws Exception {
    setValue(component, myDefaultValue);
  }
}
