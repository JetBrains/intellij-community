// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadVSpacer;

@Service(Service.Level.PROJECT)
public final class HSizePolicyProperty extends SizePolicyProperty {
  public static HSizePolicyProperty getInstance(Project project) {
    return project.getService(HSizePolicyProperty.class);
  }

  private HSizePolicyProperty(){
    super("Horizontal Size Policy");
  }

  @Override
  protected int getValueImpl(final GridConstraints constraints){
    return constraints.getHSizePolicy();
  }

  @Override
  protected void setValueImpl(final GridConstraints constraints, final int policy){
    constraints.setHSizePolicy(policy);
  }

  @Override public boolean appliesTo(final RadComponent component) {
    return !(component instanceof RadVSpacer);
  }
}
