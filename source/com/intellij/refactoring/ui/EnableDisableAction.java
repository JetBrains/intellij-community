package com.intellij.refactoring.ui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * @author dsl
 */
public abstract class EnableDisableAction extends AbstractAction {
  public void actionPerformed(ActionEvent e) {
    if (getTable().isEditing()) return;
    int[] rows = getTable().getSelectedRows();
    if (rows.length > 0) {
      boolean valueToBeSet = false;
      for (int idx = 0; idx < rows.length; idx++) {
        final int row = rows[idx];
        if (!isRowChecked(row)) {
          valueToBeSet = true;
          break;
        }
      }
      applyValue(rows, valueToBeSet);
//          myMyTableModel.fireTableRowsUpdated(rows[0], rows[rows.length - 1]);
    }
    getTable().requestFocus();
  }

  protected abstract JTable getTable();

  protected abstract void applyValue(int[] rows, boolean valueToBeSet);

  protected abstract boolean isRowChecked(int row);

  public void register() {// make SPACE check/uncheck selected rows
    JTable table = getTable();
    InputMap inputMap = table.getInputMap();
    inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "enable_disable");
    table.getActionMap().put("enable_disable", this);
  }
}
