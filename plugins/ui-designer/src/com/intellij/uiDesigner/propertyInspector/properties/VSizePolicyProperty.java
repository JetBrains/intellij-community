// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadHSpacer;

@Service(Service.Level.PROJECT)
public final class VSizePolicyProperty extends SizePolicyProperty {
  public static VSizePolicyProperty getInstance(Project project) {
    return project.getService(VSizePolicyProperty.class);
  }

  public VSizePolicyProperty() {
    super("Vertical Size Policy");
  }

  @Override
  protected int getValueImpl(final GridConstraints constraints){
    return constraints.getVSizePolicy();
  }

  @Override
  protected void setValueImpl(final GridConstraints constraints, final int policy){
    constraints.setVSizePolicy(policy);
  }

  @Override public boolean appliesTo(final RadComponent component) {
    return !(component instanceof RadHSpacer);
  }
}
