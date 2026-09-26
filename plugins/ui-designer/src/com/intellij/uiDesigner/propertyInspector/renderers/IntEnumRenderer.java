// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.propertyInspector.editors.IntEnumEditor;
import org.jetbrains.annotations.NotNull;

public final class IntEnumRenderer extends LabelPropertyRenderer<Integer> {
  private final IntEnumEditor.Pair[] myPairs;

  public IntEnumRenderer(final IntEnumEditor.Pair @NotNull [] pairs) {
    myPairs = pairs;
  }

  @Override
  protected void customize(final @NotNull Integer value) {
    // Find pair
    for(int i = myPairs.length - 1; i >= 0; i--){
      if(myPairs[i].myValue == value.intValue()){
        @NlsSafe String text = myPairs[i].myText;
        setText(text);
        return;
      }
    }
    throw new IllegalArgumentException("unknown value: " + value);
  }
}
