package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.radComponents.RadComponent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class IntEditor extends AbstractTextFieldEditor<Integer> {
  private final int myLowBoundary;

  /**
   * @param lowBoundary minimal integer value that editor accepts.
   */
  public IntEditor(final int lowBoundary){
    myLowBoundary = lowBoundary;
  }

  protected void setValueFromComponent(final RadComponent component, final Integer value) {
    myTf.setText(value.toString());
  }

  public Integer getValue() throws Exception{
    try {
      final Integer value = Integer.valueOf(myTf.getText());
      if(value.intValue() < myLowBoundary){
        throw new RuntimeException(UIDesignerBundle.message("error.value.should.not.be.less", myLowBoundary));
      }
      return value;
    }
    catch (final NumberFormatException exc) {
      throw new RuntimeException(UIDesignerBundle.message("error.not.an.integer"));
    }
  }
}

