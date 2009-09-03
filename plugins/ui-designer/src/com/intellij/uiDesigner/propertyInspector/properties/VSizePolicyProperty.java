package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadHSpacer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class VSizePolicyProperty extends SizePolicyProperty {
  public static VSizePolicyProperty getInstance(Project project) {
    return ServiceManager.getService(project, VSizePolicyProperty.class);
  }

  public VSizePolicyProperty() {
    super("Vertical Size Policy");
  }

  protected int getValueImpl(final GridConstraints constraints){
    return constraints.getVSizePolicy();
  }

  protected void setValueImpl(final GridConstraints constraints,final int policy){
    constraints.setVSizePolicy(policy);
  }

  @Override public boolean appliesTo(final RadComponent component) {
    return !(component instanceof RadHSpacer);
  }
}
