package com.intellij.uiDesigner.propertyInspector.renderers;

import org.jetbrains.annotations.NotNull;

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

  protected void customize(@NotNull final Insets value){
    setText(formatText(value)); 
  }

  public String formatText(final Insets value) {
    myBuffer.setLength(0);
    myBuffer.append('[');
    myBuffer.append(value.top).append(", ");
    myBuffer.append(value.left).append(", ");
    myBuffer.append(value.bottom).append(", ");
    myBuffer.append(value.right);
    myBuffer.append("]");

    // [jeka] important! do not use toString() on the StringBuffer that is reused
    return myBuffer.substring(0, myBuffer.length());
  }
}
