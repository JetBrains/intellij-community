package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.core.GridConstraints;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class SizePolicyRenderer extends LabelPropertyRenderer{
  private final StringBuffer myBuffer;

  public SizePolicyRenderer(){
    myBuffer=new StringBuffer();
  }

  protected void customize(final Object value){
    final int policy=((Integer)value).intValue();
    myBuffer.setLength(0);

    if((policy & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0){
      myBuffer.append("Can Shrink");
    }
    if((policy & GridConstraints.SIZEPOLICY_CAN_GROW) != 0){
      if(myBuffer.length()>0){
        myBuffer.append(", ");
      }
      myBuffer.append("Can Grow");
    }
    if((policy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0){
      if(myBuffer.length()>0){
        myBuffer.append(", ");
      }
      myBuffer.append("Want Grow");
    }

    if(policy==GridConstraints.SIZEPOLICY_FIXED){
      myBuffer.append("Fixed");
    }

    setText(myBuffer.substring(0, myBuffer.length())); // [jeka] important! do not use toString() on the StringBuffer that is reused
  }
}
