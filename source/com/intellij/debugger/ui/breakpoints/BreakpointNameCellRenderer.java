package com.intellij.debugger.ui.breakpoints;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * @author Jeka
 */
public class BreakpointNameCellRenderer extends DefaultTableCellRenderer {
  private Color myAnyExceptionForeground = new Color(128, 0, 0);

  public Component getTableCellRendererComponent(JTable table, Object value,
    boolean isSelected, boolean hasFocus, int row, int column) {
    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    BreakpointTableModel tableModel = (BreakpointTableModel)table.getModel();
    Breakpoint breakpoint = tableModel.getBreakpoint(row);
    if (breakpoint == null){
      return this;
    };
    setIcon(breakpoint.getIcon());
    setDisabledIcon(breakpoint.getIcon());

    if(isSelected){
      setForeground(UIManager.getColor("Table.selectionForeground"));
    }else{
      Color foreColor;
      if(breakpoint instanceof AnyExceptionBreakpoint){
        foreColor=myAnyExceptionForeground;
      }else{
        foreColor=UIManager.getColor("Table.foreground");
      }
      setForeground(foreColor);
    }
    setEnabled(breakpoint.ENABLED);
    return this;
  }
}
