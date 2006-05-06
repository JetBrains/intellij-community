/**
 * created at Oct 5, 2001
 * @author Jeka
 */
package com.intellij.ui;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class BooleanTableCellRenderer extends JCheckBox implements TableCellRenderer {
  private final JPanel myPanel = new JPanel();

  public BooleanTableCellRenderer() {
    super();
    setHorizontalAlignment(JLabel.CENTER);
  }

  public Component getTableCellRendererComponent(JTable table, Object value,
                                                 boolean isSelected, boolean hasFocus,
                                                 int row, int column) {
    if(value == null) {
      if(isSelected) {
        myPanel.setBackground(table.getSelectionBackground());
      }
      else {
        myPanel.setBackground(table.getBackground());
      }
      return myPanel;
    }
    if(isSelected) {
      setForeground(table.getSelectionForeground());
      super.setBackground(table.getSelectionBackground());
    }
    else {
      setForeground(table.getForeground());
      setBackground(table.getBackground());
    }
    if (value instanceof String) {
      setSelected((Boolean.parseBoolean((String)value)));            
    } else {
      setSelected(((Boolean)value).booleanValue());
    }
    return this;
  }
}
