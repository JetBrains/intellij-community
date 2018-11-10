// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;

/**
 * @author yole
 */
public class IndentProperty extends AbstractIntProperty<RadComponent> {
  public static IndentProperty getInstance(Project project) {
    return ServiceManager.getService(project, IndentProperty.class);
  }

  public IndentProperty() {
    super(null, "Indent", 0);
  }

  @Override
  public Integer getValue(RadComponent component) {
    return component.getConstraints().getIndent();
  }

  @Override
  protected void setValueImpl(RadComponent component, Integer value) throws Exception {
    final int indent = value.intValue();

    final GridConstraints constraints = component.getConstraints();
    if (constraints.getIndent() != indent) {
      GridConstraints oldConstraints = (GridConstraints)constraints.clone();
      constraints.setIndent(indent);
      component.fireConstraintsChanged(oldConstraints);
    }
  }
}
