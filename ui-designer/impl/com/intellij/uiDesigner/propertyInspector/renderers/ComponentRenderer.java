package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * This renderer is used both as PropertyRenderer and as cell renderer in the ComponentEditor
 * combo box.
 * @author yole
 */
public class ComponentRenderer extends ColoredListCellRenderer implements PropertyRenderer {
  public JComponent getComponent(final RadComponent component, Object value, boolean selected, boolean hasFocus) {
    clear();
    setBackground(selected ? UIUtil.getTableSelectionBackground() : UIUtil.getTableBackground());
    String valueId = (String) value;
    if (valueId != null && valueId.length() > 0) {
      RadComponent target = FormEditingUtil.findComponentAnywhere(component, valueId);
      if (target != null) {
        renderComponent(target, selected);
      }
      else {
        append(UIDesignerBundle.message("component.not.found"), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }

    return this;
  }

  private void renderComponent(@Nullable final RadComponent target, boolean selected) {
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
      final String componentTitle = ComponentTree.getComponentTitle(target);
      if (componentTitle != null && componentTitle.length() > "\"\"".length()) {
        append(componentTitle, baseAttributes);
      }
      else {
        append(target.getComponentClass().getSimpleName(),
               selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
  }

  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    renderComponent((RadComponent) value, false);
  }
}
