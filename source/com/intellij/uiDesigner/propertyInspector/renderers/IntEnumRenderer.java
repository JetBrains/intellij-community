package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.propertyInspector.editors.IntEnumEditor;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntEnumRenderer extends LabelPropertyRenderer{
  private final IntEnumEditor.Pair[] myPairs;

  public IntEnumRenderer(final IntEnumEditor.Pair[] pairs) {
    if (pairs == null) {
      throw new IllegalArgumentException("pairs cannot be null");
    }
    myPairs = pairs;
  }

  protected void customize(final Object value) {
    if (value == null) {
      throw new IllegalArgumentException("value cannot be null");
    }
    final Integer _int = (Integer)value;
    // Find pair
    for(int i = myPairs.length - 1; i >= 0; i--){
      if(myPairs[i].myValue == _int.intValue()){
        setText(myPairs[i].myText);
        return;
      }
    }
    throw new IllegalArgumentException("unknown value: " + value);
  }
}
