// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.radComponents.RadContainer;

import java.awt.*;

@Service(Service.Level.PROJECT)
public final class HGapProperty extends AbstractIntProperty<RadContainer> {
  public static HGapProperty getInstance(Project project) {
    return project.getService(HGapProperty.class);
  }

  public HGapProperty(){
    super(null, "Horizontal Gap", -1);
  }

  @Override
  public Integer getValue(final RadContainer component){
    if (component.getLayout() instanceof BorderLayout) {
      BorderLayout layout = (BorderLayout) component.getLayout();
      return layout.getHgap();
    }
    if (component.getLayout() instanceof FlowLayout) {
      FlowLayout layout = (FlowLayout) component.getLayout();
      return layout.getHgap();
    }
    if (component.getLayout() instanceof CardLayout) {
      CardLayout layout = (CardLayout) component.getLayout();
      return layout.getHgap();
    }
    if (component.getLayout() instanceof AbstractLayout layoutManager) {
      return layoutManager.getHGap();
    }
    return null;
  }

  @Override
  protected void setValueImpl(final RadContainer component, final Integer value) throws Exception{
    if (component.getLayout() instanceof BorderLayout) {
      BorderLayout layout = (BorderLayout) component.getLayout();
      layout.setHgap(value.intValue());
    }
    else if (component.getLayout() instanceof FlowLayout) {
      FlowLayout layout = (FlowLayout) component.getLayout();
      layout.setHgap(value.intValue());
    }
    else if (component.getLayout() instanceof CardLayout) {
      CardLayout layout = (CardLayout) component.getLayout();
      layout.setHgap(value.intValue());
    }
    else {
      final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
      layoutManager.setHGap(value.intValue());
    }
  }

  @Override protected int getDefaultValue(final RadContainer radContainer) {
    return getDefaultGap(radContainer.getLayout());
  }

  static int getDefaultGap(final LayoutManager layout) {
    if (layout instanceof FlowLayout) {
      return 5;
    }
    if (layout instanceof BorderLayout || layout instanceof CardLayout) {
      return 0;
    }
    return -1;
  }
}
