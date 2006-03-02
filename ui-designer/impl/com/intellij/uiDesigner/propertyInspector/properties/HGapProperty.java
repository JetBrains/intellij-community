package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.radComponents.RadContainer;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class HGapProperty extends AbstractIntProperty<RadContainer> {
  public HGapProperty(){
    super(null, "Horizontal Gap", -1);
  }

  public Object getValue(final RadContainer component){
    final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
    return layoutManager.getHGap();
  }

  protected void setValueImpl(final RadContainer component,final Object value) throws Exception{
    final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
    layoutManager.setHGap(((Integer)value).intValue());
  }
}
