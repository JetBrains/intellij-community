package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class MaximumSizeProperty extends AbstractDimensionPropery{
  public MaximumSizeProperty(){
    super("Maximum Size");
  }

  public Object getValue(final RadComponent component){
    return component.getConstraints().myMaximumSize;
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception{
    component.getConstraints().myMaximumSize.setSize((Dimension)value);
  }
}
