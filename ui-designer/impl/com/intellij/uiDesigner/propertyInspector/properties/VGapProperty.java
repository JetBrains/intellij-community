package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.openapi.project.Project;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.CardLayout;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 * @author yole
 */
public final class VGapProperty extends AbstractIntProperty<RadContainer> {
  public static VGapProperty getInstance(Project project) {
    return project.getComponent(VGapProperty.class);
  }

  public VGapProperty(){
    super(null," Vertical Gap", -1);
  }

  public Integer getValue(final RadContainer component) {
    if (component.getLayout() instanceof BorderLayout) {
      BorderLayout layout = (BorderLayout) component.getLayout();
      return layout.getVgap();
    }
    if (component.getLayout() instanceof FlowLayout) {
      FlowLayout layout = (FlowLayout) component.getLayout();
      return layout.getVgap();
    }
    if (component.getLayout() instanceof CardLayout) {
      CardLayout layout = (CardLayout) component.getLayout();
      return layout.getVgap();
    }
    final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
    return layoutManager.getVGap();
  }

  protected void setValueImpl(final RadContainer component, final Integer value) throws Exception {
    if (component.getLayout() instanceof BorderLayout) {
      BorderLayout layout = (BorderLayout) component.getLayout();
      layout.setVgap(value.intValue());
    }
    else if (component.getLayout() instanceof FlowLayout) {
      FlowLayout layout = (FlowLayout) component.getLayout();
      layout.setVgap(value.intValue());
    }
    else if (component.getLayout() instanceof CardLayout) {
      CardLayout layout = (CardLayout) component.getLayout();
      layout.setVgap(value.intValue());
    }
    else {
      final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
      layoutManager.setVGap(value.intValue());
    }
  }

  @Override protected int getDefaultValue(final RadContainer radContainer) {
    return HGapProperty.getDefaultGap(radContainer.getLayout());
  }
}
