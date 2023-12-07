// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class InsetsPropertyRenderer extends LabelPropertyRenderer<Insets> {
  private final StringBuffer myBuffer;

  public InsetsPropertyRenderer(){
    myBuffer=new StringBuffer();
  }

  @Override
  protected void customize(final @NotNull Insets value){
    setText(formatText(value));
  }

  public @NlsSafe String formatText(final Insets value) {
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
