// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;


public class UseParentLayoutProperty extends AbstractBooleanProperty<RadComponent> {
  public static UseParentLayoutProperty getInstance(Project project) {
    return project.getService(UseParentLayoutProperty.class);
  }

  public UseParentLayoutProperty() {
    super(null, "Align Grid with Parent", false);
  }

  @Override
  public Boolean getValue(RadComponent component) {
    return component.getConstraints().isUseParentLayout();
  }

  @Override
  protected void setValueImpl(RadComponent component, Boolean value) throws Exception {
    final boolean useParentLayout = value.booleanValue();

    final GridConstraints constraints = component.getConstraints();
    if (constraints.isUseParentLayout() != useParentLayout) {
      GridConstraints oldConstraints = (GridConstraints)constraints.clone();
      constraints.setUseParentLayout(useParentLayout);
      component.fireConstraintsChanged(oldConstraints);
    }
  }
  @Override public boolean appliesTo(RadComponent component) {
    return component instanceof RadContainer && ((RadContainer)component).getLayoutManager().isGrid() && component.getParent().getLayoutManager().isGrid();
  }
}
