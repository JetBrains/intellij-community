// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.SimpleListCellRenderer;
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
import java.util.Objects;


public class LayoutManagerProperty extends Property<RadContainer, String> {
  private final PropertyRenderer<String> myRenderer = new LabelPropertyRenderer<>() {
    @Override
    protected void customize(final @NotNull String value) {
      setText(LayoutManagerRegistry.getLayoutManagerDisplayName(value));
    }
  };

  private static class LayoutManagerEditor extends ComboBoxPropertyEditor<String> {
    LayoutManagerEditor() {
      myCbx.setRenderer(SimpleListCellRenderer.create("", LayoutManagerRegistry::getLayoutManagerDisplayName));
    }

    @Override
    public JComponent getComponent(RadComponent component, @NlsSafe String value, InplaceContext inplaceContext) {
      if (UIFormXmlConstants.LAYOUT_XY.equals(value)) {
        myCbx.setModel(new DefaultComboBoxModel<>(LayoutManagerRegistry.getLayoutManagerNames()));
      }
      else {
        myCbx.setModel(new DefaultComboBoxModel<>(LayoutManagerRegistry.getNonDeprecatedLayoutManagerNames()));
      }
      myCbx.setSelectedItem(value);
      return myCbx;
    }
  }

  private final PropertyEditor<String> myEditor = new LayoutManagerEditor();

  public LayoutManagerProperty() {
    super(null, "Layout Manager");
  }

  @Override
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

  @Override
  protected void setValueImpl(RadContainer component, String value) throws Exception {
    final RadLayoutManager oldLayout = component.getLayoutManager();
    if (oldLayout != null && Objects.equals(oldLayout.getName(), value)) {
      return;
    }

    RadLayoutManager newLayoutManager = LayoutManagerRegistry.createLayoutManager(value);
    newLayoutManager.changeContainerLayout(component);
  }

  @Override
  public @NotNull PropertyRenderer<String> getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor<String> getEditor() {
    return myEditor;
  }

  @Override
  public boolean needRefreshPropertyList() {
    return true;
  }
}
