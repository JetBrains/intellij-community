package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadContainer;
import com.intellij.uiDesigner.core.AbstractLayout;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class VGapProperty extends AbstractIntProperty{
  public VGapProperty(){
    super(null," Vertical Gap", -1);
  }

  public Object getValue(final RadComponent component){
    if(!(component instanceof RadContainer)){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("component must be an instance of RadContainer: "+component);
    }
    final RadContainer container=(RadContainer)component;
    final AbstractLayout layoutManager=(AbstractLayout)container.getLayout();
    return new Integer(layoutManager.getVGap());
  }

  protected void setValueImpl(final RadComponent component,final Object value) throws Exception{
    if(!(component instanceof RadContainer)){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("component must be an instance of RadContainer: "+component);
    }
    final RadContainer container=(RadContainer)component;
    final AbstractLayout layoutManager=(AbstractLayout)container.getLayout();
    layoutManager.setVGap(((Integer)value).intValue());
  }
}
