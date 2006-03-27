package com.intellij.uiDesigner.propertyInspector.renderers;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class RectangleRenderer extends LabelPropertyRenderer<Rectangle> {
  private final StringBuffer myBuffer;

  public RectangleRenderer(){
    myBuffer=new StringBuffer();
  }

  protected void customize(final Rectangle value){
    myBuffer.setLength(0);
    myBuffer.append('[').append(value.x).append(", ");
    myBuffer.append(value.y).append(", ");
    myBuffer.append(value.width).append(", ");
    myBuffer.append(value.height).append("]");

    setText(myBuffer.substring(0, myBuffer.length())); // [jeka] important! do not use toString() on the StringBuffer that is reused
  }
}
