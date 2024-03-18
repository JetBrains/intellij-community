// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.radComponents.RadContainer;

import java.awt.*;

@Service(Service.Level.PROJECT)
public final class VGapProperty extends AbstractIntProperty<RadContainer> {
  public static VGapProperty getInstance(Project project) {
    return project.getService(VGapProperty.class);
  }

  public VGapProperty(){
    super(null,"Vertical Gap", -1);
  }

  @Override
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
    if (component.getLayout() instanceof AbstractLayout layoutManager) {
      return layoutManager.getVGap();
    }
    return null;
  }

  @Override
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
