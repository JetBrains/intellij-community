// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.IntRegexEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.DimensionRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * This class is a base for implementing such properties
 * as "minimum size", "preferred size" and "maximum size".
 */
public abstract class AbstractDimensionProperty<T extends RadComponent> extends Property<T, Dimension> {
  private final Property[] myChildren;
  private final DimensionRenderer myRenderer;
  private final IntRegexEditor<Dimension> myEditor;

  public AbstractDimensionProperty(final @NonNls String name){
    super(null, name);
    myChildren=new Property[]{
      new IntFieldProperty(this, "width", -1, JBUI.emptySize()),
      new IntFieldProperty(this, "height", -1, JBUI.emptySize()),
    };
    myRenderer = new DimensionRenderer();
    myEditor = new IntRegexEditor<>(Dimension.class, myRenderer, new int[]{-1, -1});
  }

  @Override
  public final Property @NotNull [] getChildren(final RadComponent component){
    return myChildren;
  }

  @Override
  public final @NotNull PropertyRenderer<Dimension> getRenderer() {
    return myRenderer;
  }

  @Override
  public final PropertyEditor<Dimension> getEditor() {
    return myEditor;
  }

  @Override
  public Dimension getValue(T component) {
    return getValueImpl(component.getConstraints());
  }

  protected abstract Dimension getValueImpl(final GridConstraints constraints);

  @Override public boolean isModified(final T component) {
    final Dimension defaultValue = getValueImpl(FormEditingUtil.getDefaultConstraints(component));
    return !getValueImpl(component.getConstraints()).equals(defaultValue);
  }

  @Override public void resetValue(T component) throws Exception {
    setValueImpl(component, getValueImpl(FormEditingUtil.getDefaultConstraints(component)));
  }
}
