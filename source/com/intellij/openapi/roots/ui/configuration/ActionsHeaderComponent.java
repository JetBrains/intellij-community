package com.intellij.openapi.roots.ui.configuration;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 31
 * @author 2003
 */
public class ActionsHeaderComponent extends JPanel/*ScalableIconComponent*/ {
  private static final int HEADER_Y_OFFSET = 4;
  private JLabel myHeaderLabel;

  public void addNotify() {
    super.addNotify();
    final Dimension preferredSize = new Dimension(0, 0);
    calcPreferredSize(this, preferredSize);
    preferredSize.height += HEADER_Y_OFFSET;
    this.setPreferredSize(preferredSize);
  }

  public void setHeaderTextColor(Color color) {
    myHeaderLabel.setForeground(color);
  }

  private void calcPreferredSize(Container container, Dimension dimension) {
    final Component[] components = container.getComponents();
    int maxWidth = 0;
    int maxHeight = 0;
    for (Component component : components) {
      if (component instanceof Container) {
        calcPreferredSize((Container)component, dimension);
        maxWidth = Math.max(maxWidth, dimension.width);
        maxHeight = Math.max(maxHeight, dimension.height);
      }
      else {
        final Dimension preferredSize = component.getPreferredSize();
        maxWidth = Math.max(maxWidth, preferredSize.width);
        maxHeight = Math.max(maxHeight, preferredSize.height);
      }
    }
    final Dimension preferredSize = container.getPreferredSize();
    dimension.width = Math.max(maxWidth, preferredSize.width);
    dimension.height = Math.max(maxHeight, preferredSize.height);
  }

  public final void setSelected(boolean isSelected) {
    setBackground(isSelected? UIManager.getColor("Table.selectionBackground") : UIManager.getColor("Table.textBackground"));
    myHeaderLabel.setForeground(isSelected? UIManager.getColor("Table.selectionForeground") : UIManager.getColor("Table.textForeground"));
    this.revalidate();
    this.repaint();
  }
}
