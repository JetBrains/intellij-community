package com.intellij.refactoring.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: Oct 18, 2002
 * Time: 5:09:33 PM
 * To change this template use Options | File Templates.
 */
public class ColorConfiguringCellRenderer extends DefaultTableCellRenderer {
  protected void configureColors(boolean isSelected, JTable table, boolean hasFocus, final int row, final int column) {

    if (isSelected) {
      setForeground(table.getSelectionForeground());
    }
    else {
      setForeground(UIManager.getColor("Table.foreground"));
    }


    if (hasFocus) {
      setForeground(UIManager.getColor("Table.focusCellForeground"));
    }
  }
}
