package com.intellij.uiDesigner.propertyInspector.renderers;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RectangleRenderer extends LabelPropertyRenderer{
  private final StringBuffer myBuffer;

  public RectangleRenderer(){
    myBuffer=new StringBuffer();
  }

  protected void customize(final Object value){
    final Rectangle r=(Rectangle)value;

    myBuffer.setLength(0);
    myBuffer.append('[').append(r.x).append(", ");
    myBuffer.append(r.y).append(", ");
    myBuffer.append(r.width).append(", ");
    myBuffer.append(r.height).append("]");

    setText(myBuffer.substring(0, myBuffer.length())); // [jeka] important! do not use toString() on the StringBuffer that is reused
  }
}
