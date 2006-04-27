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
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class UseParentLayoutProperty extends Property<RadComponent, Boolean> {
  public static UseParentLayoutProperty getInstance(Project project) {
    return project.getComponent(UseParentLayoutProperty.class);
  }

  private BooleanRenderer myRenderer = new BooleanRenderer();
  private BooleanEditor myEditor = new BooleanEditor();

  public UseParentLayoutProperty() {
    super(null, "Align Grid with Parent");
  }

  public Boolean getValue(RadComponent component) {
    return component.getConstraints().isUseParentLayout();
  }

  protected void setValueImpl(RadComponent component, Boolean value) throws Exception {
    final boolean useParentLayout = value.booleanValue();

    final GridConstraints constraints = component.getConstraints();
    if (constraints.isUseParentLayout() != useParentLayout) {
      GridConstraints oldConstraints = (GridConstraints)constraints.clone();
      constraints.setUseParentLayout(useParentLayout);
      component.fireConstraintsChanged(oldConstraints);
    }
  }

  @NotNull public PropertyRenderer<Boolean> getRenderer() {
    return myRenderer;
  }

  @Nullable public PropertyEditor<Boolean> getEditor() {
    return myEditor;
  }

  @Override public boolean appliesTo(RadComponent component) {
    return component instanceof RadContainer && ((RadContainer)component).getLayoutManager().isGrid() && component.getParent().getLayoutManager().isGrid();
  }

  @Override public boolean isModified(final RadComponent component) {
    return component.getConstraints().isUseParentLayout();
  }
}
