package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.propertyInspector.editors.IntEnumEditor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntEnumRenderer extends LabelPropertyRenderer {
  private final IntEnumEditor.Pair[] myPairs;

  public IntEnumRenderer(@NotNull final IntEnumEditor.Pair[] pairs) {
    myPairs = pairs;
  }

  protected void customize(@NotNull final Object value) {
    final Integer _int = (Integer)value;
    // Find pair
    for(int i = myPairs.length - 1; i >= 0; i--){
      if(myPairs[i].myValue == _int.intValue()){
        setText(myPairs[i].myText);
        return;
      }
    }
    //noinspection HardCodedStringLiteral
    throw new IllegalArgumentException("unknown value: " + value);
  }
}
