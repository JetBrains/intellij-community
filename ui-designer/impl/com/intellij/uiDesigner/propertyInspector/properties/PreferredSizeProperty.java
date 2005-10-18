package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;

import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class PreferredSizeProperty extends AbstractDimensionPropery{
  public PreferredSizeProperty(){
    super("Preferred Size");
  }

  public Object getValue(final RadComponent component){
    return component.getConstraints().myPreferredSize;
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception{
    component.getConstraints().myPreferredSize.setSize((Dimension)value);
  }
}
