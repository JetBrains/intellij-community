package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.psi.PsiKeyword;

import java.awt.Insets;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class InsetsPropertyRenderer extends LabelPropertyRenderer<Insets> {
  private final StringBuffer myBuffer;

  public InsetsPropertyRenderer(){
    myBuffer=new StringBuffer();
  }

  protected void customize(final Insets value){
    myBuffer.setLength(0);
    myBuffer.append('[');
    if (value != null) {
      myBuffer.append(value.top).append(", ");
      myBuffer.append(value.left).append(", ");
      myBuffer.append(value.bottom).append(", ");
      myBuffer.append(value.right);
    }
    else {
      myBuffer.append(PsiKeyword.NULL);
    }
    myBuffer.append("]");

    setText(myBuffer.substring(0, myBuffer.length())); // [jeka] important! do not use toString() on the StringBuffer that is reused
  }
}
