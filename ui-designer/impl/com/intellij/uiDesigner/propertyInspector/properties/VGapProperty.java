package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.radComponents.RadContainer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class VGapProperty extends AbstractIntProperty<RadContainer> {
  public static final VGapProperty INSTANCE = new VGapProperty();

  public VGapProperty(){
    super(null," Vertical Gap", -1);
  }

  public Object getValue(final RadContainer component) {
    if (component.getLayout() instanceof BorderLayout) {
      BorderLayout layout = (BorderLayout) component.getLayout();
      return layout.getVgap();
    }
    if (component.getLayout() instanceof FlowLayout) {
      FlowLayout layout = (FlowLayout) component.getLayout();
      return layout.getVgap();
    }
    final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
    return layoutManager.getVGap();
  }

  protected void setValueImpl(final RadContainer component,final Object value) throws Exception {
    if (component.getLayout() instanceof BorderLayout) {
      BorderLayout layout = (BorderLayout) component.getLayout();
      layout.setVgap(((Integer) value).intValue());
    }
    else if (component.getLayout() instanceof FlowLayout) {
      FlowLayout layout = (FlowLayout) component.getLayout();
      layout.setVgap(((Integer) value).intValue());
    }
    else {
      final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
      layoutManager.setVGap(((Integer)value).intValue());
    }
  }
}
