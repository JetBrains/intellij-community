// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;

import java.awt.*;

@Service(Service.Level.PROJECT)
public final class MaximumSizeProperty extends AbstractDimensionProperty<RadComponent> {
  public static MaximumSizeProperty getInstance(Project project) {
    return project.getService(MaximumSizeProperty.class);
  }

  public MaximumSizeProperty(){
    super("Maximum Size");
  }

  @Override
  protected Dimension getValueImpl(final GridConstraints constraints) {
    return constraints.myMaximumSize;
  }

  @Override
  protected void setValueImpl(final RadComponent component, final Dimension value) throws Exception{
    component.getConstraints().myMaximumSize.setSize(value);
  }
}
