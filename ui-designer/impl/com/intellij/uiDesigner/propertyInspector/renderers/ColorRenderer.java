package com.intellij.uiDesigner.propertyInspector.renderers;

import com.intellij.uiDesigner.lw.ColorDescriptor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;

/**
 * This renderer is used both as PropertyRenderer and as cell renderer in the color chooser pane list.
 * @author yole
 */
public class ColorRenderer extends ColoredListCellRenderer implements PropertyRenderer<ColorDescriptor> {
  private ColorDescriptor myColorDescriptor;
  private final Icon myEmptyIcon = IconLoader.getIcon("/com/intellij/uiDesigner/icons/empty.png");

  public ColorRenderer() {
    setOpaque(true);
  }

  public JComponent getComponent(RadRootContainer rootContainer, ColorDescriptor value, boolean selected, boolean hasFocus) {
    prepareComponent(value, selected);
    return this;
  }

  private void prepareComponent(final ColorDescriptor value, final boolean selected) {
    myColorDescriptor = value;
    clear();
    setIcon(myEmptyIcon);
    setBackground(selected ? UIUtil.getTableSelectionBackground() : UIUtil.getTableBackground());
    if (myColorDescriptor != null) {
      append(myColorDescriptor.toString(),
             selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }
  }

  @Override protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (myColorDescriptor != null) {
      final int size = getBounds().height;
      g.setColor(getBackground());
      g.fillRect(0, 0, size+getIconTextGap()+1, size);
      g.setColor(myColorDescriptor.getResolvedColor());
      g.fillRect(2, 2, size-4, size-4);
      g.setColor(Color.BLACK);
      g.drawRect(2, 2, size-4, size-4);
    }
  }

  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    prepareComponent((ColorDescriptor) value, selected);
  }
}
