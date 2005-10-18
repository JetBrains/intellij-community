package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.GridConstraints;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class VSizePolicyProperty extends SizePolicyProperty{
  public VSizePolicyProperty(){
    super("Vertical Size Policy");
  }

  protected int getValueImpl(final GridConstraints constraints){
    return constraints.getVSizePolicy();
  }

  protected void setValueImpl(final GridConstraints constraints,final int policy){
    constraints.setVSizePolicy(policy);
  }
}
