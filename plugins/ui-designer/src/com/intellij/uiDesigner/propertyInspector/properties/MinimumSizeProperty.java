// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;

import java.awt.*;

@Service(Service.Level.PROJECT)
public final class MinimumSizeProperty extends AbstractDimensionProperty<RadComponent> {
  public static MinimumSizeProperty getInstance(Project project) {
    return project.getService(MinimumSizeProperty.class);
  }

  public MinimumSizeProperty(){
    super("Minimum Size");
  }

  @Override
  protected Dimension getValueImpl(final GridConstraints constraints) {
    return constraints.myMinimumSize;
  }

  @Override
  protected void setValueImpl(final RadComponent component, final Dimension value) throws Exception{
    component.getConstraints().myMinimumSize.setSize(value);
  }
}
