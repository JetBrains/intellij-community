package com.intellij.ui;

import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public abstract class ColoredTableCellRenderer extends SimpleColoredRenderer implements TableCellRenderer {
  public final Component getTableCellRendererComponent(
    JTable table,
    Object value,
    boolean isSelected,
    boolean hasFocus,
    int row,
    int column
  ){
    clear();
    setPaintFocusBorder(hasFocus);
    acquireState(table, isSelected, hasFocus, row, column);
    getCellState().updateRenderer(this);
    customizeCellRenderer(table, value, isSelected, hasFocus, row, column);
    return this;
  }

  protected abstract void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column);
}
