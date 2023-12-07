// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.NotNull;

public final class SizePolicyRenderer extends LabelPropertyRenderer<Integer> {
  private final StringBuffer myBuffer;

  public SizePolicyRenderer(){
    myBuffer=new StringBuffer();
  }

  @Override
  protected void customize(final @NotNull Integer value) {
    final int policy=value.intValue();
    myBuffer.setLength(0);

    if((policy & GridConstraints.SIZEPOLICY_CAN_SHRINK) != 0){
      myBuffer.append(UIDesignerBundle.message("property.can.shrink"));
    }
    if((policy & GridConstraints.SIZEPOLICY_CAN_GROW) != 0){
      if(myBuffer.length()>0){
        myBuffer.append(", ");
      }
      myBuffer.append(UIDesignerBundle.message("property.can.grow"));
    }
    if((policy & GridConstraints.SIZEPOLICY_WANT_GROW) != 0){
      if(myBuffer.length()>0){
        myBuffer.append(", ");
      }
      myBuffer.append(UIDesignerBundle.message("property.want.grow"));
    }

    if(policy==GridConstraints.SIZEPOLICY_FIXED){
      myBuffer.append(UIDesignerBundle.message("property.fixed"));
    }

    @NlsSafe String text = myBuffer.substring(0, myBuffer.length()); // [jeka] important! do not use toString() on the StringBuffer that is reused
    setText(text);
  }
}
