package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class PreferredSizeProperty extends AbstractDimensionPropery{
  public PreferredSizeProperty(){
    super("Preferred Size");
  }

  protected Dimension getValueImpl(final GridConstraints constraints) {
    return constraints.myPreferredSize;
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception{
    component.getConstraints().myPreferredSize.setSize((Dimension)value);
  }
}
