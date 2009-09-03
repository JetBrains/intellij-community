package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadVSpacer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class HSizePolicyProperty extends SizePolicyProperty {
  public static HSizePolicyProperty getInstance(Project project) {
    return ServiceManager.getService(project, HSizePolicyProperty.class);
  }

  private HSizePolicyProperty(){
    super("Horizontal Size Policy");
  }

  protected int getValueImpl(final GridConstraints constraints){
    return constraints.getHSizePolicy();
  }

  protected void setValueImpl(final GridConstraints constraints,final int policy){
    constraints.setHSizePolicy(policy);
  }

  @Override public boolean appliesTo(final RadComponent component) {
    return !(component instanceof RadVSpacer);
  }
}
