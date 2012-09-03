/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ComboBoxPropertyEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.LayoutManagerRegistry;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadLayoutManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class LayoutManagerProperty extends Property<RadContainer, String> {
  private final PropertyRenderer<String> myRenderer = new LabelPropertyRenderer<String>() {
    @Override
    protected void customize(@NotNull final String value) {
      setText(LayoutManagerRegistry.getLayoutManagerDisplayName(value));
    }
  };

  private static class LayoutManagerEditor extends ComboBoxPropertyEditor<String> {
    public LayoutManagerEditor() {
      myCbx.setRenderer(new ListCellRendererWrapper<String>() {
        @Override
        public void customize(JList list, String value, int index, boolean selected, boolean hasFocus) {
          setText(LayoutManagerRegistry.getLayoutManagerDisplayName(value));
        }
      });
    }

    public JComponent getComponent(RadComponent component, String value, InplaceContext inplaceContext) {
      if (UIFormXmlConstants.LAYOUT_XY.equals(value)) {
        myCbx.setModel(new DefaultComboBoxModel(LayoutManagerRegistry.getLayoutManagerNames()));
      }
      else {
        myCbx.setModel(new DefaultComboBoxModel(LayoutManagerRegistry.getNonDeprecatedLayoutManagerNames()));
      }
      myCbx.setSelectedItem(value);
      return myCbx;
    }
  }

  private final PropertyEditor<String> myEditor = new LayoutManagerEditor();

  public LayoutManagerProperty() {
    super(null, "Layout Manager");
  }

  public String getValue(RadContainer component) {
    RadContainer container = component;
    while(container != null) {
      final RadLayoutManager layoutManager = container.getLayoutManager();
      if (layoutManager != null) {
        return layoutManager.getName();
      }
      container = container.getParent();
    }
    return UIFormXmlConstants.LAYOUT_INTELLIJ;
  }

  protected void setValueImpl(RadContainer component, String value) throws Exception {
    final RadLayoutManager oldLayout = component.getLayoutManager();
    if (oldLayout != null && Comparing.equal(oldLayout.getName(), value)) {
      return;
    }

    RadLayoutManager newLayoutManager = LayoutManagerRegistry.createLayoutManager(value);
    newLayoutManager.changeContainerLayout(component);
  }

  @NotNull public PropertyRenderer<String> getRenderer() {
    return myRenderer;
  }

  public PropertyEditor<String> getEditor() {
    return myEditor;
  }

  @Override
  public boolean needRefreshPropertyList() {
    return true;
  }
}
