// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.openapi.util.NlsSafe;

import java.awt.*;

public final class RectangleRenderer extends LabelPropertyRenderer<Rectangle> {
  private final StringBuffer myBuffer;

  public RectangleRenderer(){
    myBuffer=new StringBuffer();
  }

  @Override
  protected void customize(final Rectangle value){
    myBuffer.setLength(0);
    myBuffer.append('[').append(value.x).append(", ");
    myBuffer.append(value.y).append(", ");
    myBuffer.append(value.width).append(", ");
    myBuffer.append(value.height).append("]");

    @NlsSafe String text = myBuffer.substring(0, myBuffer.length()); // [jeka] important! do not use toString() on the StringBuffer that is reused
    setText(text);
  }
}
