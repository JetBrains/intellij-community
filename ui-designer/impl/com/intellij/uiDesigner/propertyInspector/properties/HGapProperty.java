package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.radComponents.RadContainer;

import java.awt.BorderLayout;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class HGapProperty extends AbstractIntProperty<RadContainer> {
  public static final HGapProperty INSTANCE = new HGapProperty();

  public HGapProperty(){
    super(null, "Horizontal Gap", -1);
  }

  public Object getValue(final RadContainer component){
    if (component.getLayout() instanceof BorderLayout) {
      BorderLayout layout = (BorderLayout) component.getLayout();
      return layout.getHgap();
    }
    final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
    return layoutManager.getHGap();
  }

  protected void setValueImpl(final RadContainer component,final Object value) throws Exception{
    if (component.getLayout() instanceof BorderLayout) {
      BorderLayout layout = (BorderLayout) component.getLayout();
      layout.setHgap(((Integer) value).intValue());
    }
    else {
      final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
      layoutManager.setHGap(((Integer)value).intValue());
    }
  }
}
