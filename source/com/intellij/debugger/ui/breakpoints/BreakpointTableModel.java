/*
 * Class BreakpointTableModel
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.util.ui.ItemRemovable;

import javax.swing.table.AbstractTableModel;
import java.util.LinkedList;

public class BreakpointTableModel extends AbstractTableModel implements ItemRemovable {
  public static final int ENABLED_STATE = 0;
  public static final int NAME = 1;

  private java.util.List myBreakpoints = null;

  public BreakpointTableModel() {
    myBreakpoints = new LinkedList();
  }

  public final void setBreakpoints(Breakpoint[] breakpoints) {
    myBreakpoints.clear();
    if (breakpoints != null) {
      for (int idx = 0; idx < breakpoints.length; idx++) {
        myBreakpoints.add(breakpoints[idx]);
      }
    }
    fireTableDataChanged();
  }

  public Breakpoint getBreakpoint(int index) {
    if (index < 0 || index >= myBreakpoints.size()) return null;
    return (Breakpoint)myBreakpoints.get(index);
  }

  public int getBreakpointIndex(Breakpoint breakpoint) {
    return myBreakpoints.indexOf(breakpoint);
  }

  public void insertBreakpointAt(Breakpoint breakpoint, int index) {
    myBreakpoints.add(index, breakpoint);
    fireTableRowsInserted(index, index);
  }

  public void addBreakpoint(Breakpoint breakpoint) {
    myBreakpoints.add(breakpoint);
    int row = myBreakpoints.size() - 1;
    fireTableRowsInserted(row, row);
  }

  public void removeRow(int idx) {
    if (idx >= 0 && idx < myBreakpoints.size()) {
      myBreakpoints.remove(idx);
      fireTableRowsDeleted(idx, idx);
    }
  }

  public int getRowCount() {
    return myBreakpoints.size();
  }

  public int getColumnCount() {
    return 2;
  }

  public String getColumnName(int column) {
    switch (column) {
    case ENABLED_STATE:
      return "Enabled";
    case NAME:
      return "Name";
    default           :
      return "";
    }
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    Breakpoint breakpoint = (Breakpoint)myBreakpoints.get(rowIndex);
    if (columnIndex == NAME) {
      return breakpoint.getDisplayName();
    }
    if (columnIndex == ENABLED_STATE) {
      return breakpoint.ENABLED? Boolean.TRUE : Boolean.FALSE;
    }
    return null;
  }

  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    if (rowIndex < 0 || rowIndex >= myBreakpoints.size()) {
      return;
    }
    Breakpoint breakpoint = (Breakpoint)myBreakpoints.get(rowIndex);
/*
    if (columnIndex == NAME) {
      breakpoint.setDisplayName((aValue != null)? aValue.toString() : "");
    }
    else
*/
    if (columnIndex == ENABLED_STATE) {
      boolean value = aValue != null? ((Boolean)aValue).booleanValue() : true;
      breakpoint.ENABLED = value;
    }
    fireTableRowsUpdated(rowIndex, rowIndex);
  }

  public Class getColumnClass(int columnIndex) {
    if (columnIndex == ENABLED_STATE) {
      return Boolean.class;
    }
    return super.getColumnClass(columnIndex);
  }

  public boolean isCellEditable(int rowIndex, int columnIndex) {
//    Breakpoint breakpoint = (Breakpoint)myBreakpoints.get(rowIndex);
    return (columnIndex == ENABLED_STATE /*|| breakpoint.isChecked()*/);
  }
}
