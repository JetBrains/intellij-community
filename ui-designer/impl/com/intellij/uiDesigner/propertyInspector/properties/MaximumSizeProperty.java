package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class MaximumSizeProperty extends AbstractDimensionPropery{
  public MaximumSizeProperty(){
    super("Maximum Size");
  }

  protected Dimension getValueImpl(final GridConstraints constraints) {
    return constraints.myMaximumSize;
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception{
    component.getConstraints().myMaximumSize.setSize((Dimension)value);
  }
}
