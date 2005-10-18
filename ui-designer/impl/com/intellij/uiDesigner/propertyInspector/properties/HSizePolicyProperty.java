package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.GridConstraints;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class HSizePolicyProperty extends SizePolicyProperty{
  public HSizePolicyProperty(){
    super("Horizontal Size Policy");
  }

  protected int getValueImpl(final GridConstraints constraints){
    return constraints.getHSizePolicy();
  }

  protected void setValueImpl(final GridConstraints constraints,final int policy){
    constraints.setHSizePolicy(policy);
  }
}
