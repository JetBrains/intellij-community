/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ServiceManager;

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
    return ServiceManager.getService(project, VGapProperty.class);
  }

  public VGapProperty(){
    super(null,"Vertical Gap", -1);
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
    if (component.getLayout() instanceof AbstractLayout) {
      final AbstractLayout layoutManager=(AbstractLayout)component.getLayout();
      return layoutManager.getVGap();
    }
    return null;
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
