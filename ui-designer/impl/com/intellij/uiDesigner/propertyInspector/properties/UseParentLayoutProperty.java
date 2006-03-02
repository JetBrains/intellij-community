/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.BooleanEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class UseParentLayoutProperty extends Property<RadComponent> {
  private BooleanRenderer myRenderer = new BooleanRenderer();
  private BooleanEditor myEditor = new BooleanEditor();

  public UseParentLayoutProperty() {
    super(null, "Align Grid with Parent");
  }

  public Object getValue(RadComponent component) {
    return component.getConstraints().isUseParentLayout();
  }

  protected void setValueImpl(RadComponent component, Object value) throws Exception {
    final boolean useParentLayout = ((Boolean)value).booleanValue();

    final GridConstraints constraints = component.getConstraints();
    if (constraints.isUseParentLayout() != useParentLayout) {
      GridConstraints oldConstraints = (GridConstraints)constraints.clone();
      constraints.setUseParentLayout(useParentLayout);
      component.fireConstraintsChanged(oldConstraints);
    }
  }

  @NotNull public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Nullable public PropertyEditor getEditor() {
    return myEditor;
  }

  @Override public boolean appliesTo(RadComponent component) {
    return component instanceof RadContainer && component.getParent().isGrid();
  }

  @Override public boolean isModified(final RadComponent component) {
    return component.getConstraints().isUseParentLayout();
  }
}
