package com.intellij.uiDesigner.propertyInspector.renderers;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class InsetsPropertyRenderer extends LabelPropertyRenderer{
  private final StringBuffer myBuffer;

  public InsetsPropertyRenderer(){
    myBuffer=new StringBuffer();
  }

  protected void customize(final Object value){
    final Insets insets=(Insets)value;
    myBuffer.setLength(0);
    myBuffer.append('[').append(insets.top).append(", ");
    myBuffer.append(insets.left).append(", ");
    myBuffer.append(insets.bottom).append(", ");
    myBuffer.append(insets.right).append("]");

    setText(myBuffer.substring(0, myBuffer.length())); // [jeka] important! do not use toString() on the StringBuffer that is reused
  }
}
