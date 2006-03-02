package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.core.GridConstraints;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class MinimumSizeProperty extends AbstractDimensionProperty<RadComponent> {
  public MinimumSizeProperty(){
    super("Minimum Size");
  }

  protected Dimension getValueImpl(final GridConstraints constraints) {
    return constraints.myMinimumSize;
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception{
    component.getConstraints().myMinimumSize.setSize((Dimension)value);
  }
}
