// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * This renderer is used both as PropertyRenderer and as cell renderer in the ComponentEditor
 * combo box.
 */
public class ComponentRenderer extends ColoredListCellRenderer implements PropertyRenderer<String> {
  @Override
  public JComponent getComponent(final RadRootContainer rootContainer, String value, boolean selected, boolean hasFocus) {
    clear();
    setBackground(selected ? UIUtil.getTableSelectionBackground(true) : UIUtil.getTableBackground());
    if (value != null && !value.isEmpty()) {
      RadComponent target = (RadComponent)FormEditingUtil.findComponent(rootContainer, value);
      if (target != null) {
        renderComponent(target, selected);
      }
      else {
        append(UIDesignerBundle.message("component.not.found"), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    return this;
  }

  private void renderComponent(final @Nullable RadComponent target, boolean selected) {
    clear();
    final SimpleTextAttributes baseAttributes =
      selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
    if (target == null) {
      append(UIDesignerBundle.message("component.none"), baseAttributes);
      return;
    }
    setIcon(ComponentTree.getComponentIcon(target));
    String binding = target.getBinding();
    if (binding != null) {
      append(binding, baseAttributes);
    }
    else {
      final String componentTitle = target.getComponentTitle();
      if (componentTitle != null && componentTitle.length() > "\"\"".length()) {
        append(componentTitle, baseAttributes);
      }
      else {
        @NlsSafe String name = target.getComponentClass().getSimpleName();
        append(name, selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
  }

  @Override
  protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
    renderComponent((RadComponent) value, false);
  }
}
