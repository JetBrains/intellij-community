package com.intellij.application.options.pathMacros;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 *  @author dsl
 */
public class PathMacroTable extends Table {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.pathMacros.PathMacroTable");
  private PathMacrosImpl myPathMacros = PathMacrosImpl.getInstanceEx();
  private MyTableModel myTableModel = new MyTableModel();
  private static final int NAME_COLUMN = 0;
  private static final int VALUE_COLUMN = 1;
  private final boolean myEditOnlyPaths;  // if true, disable macro name changing, and macro adding/removing

  private List<Pair<String, String>> myMacros = new ArrayList<Pair<String,String>>();
  private static final Comparator<Pair<String, String>> MACRO_COMPARATOR = new Comparator<Pair<String, String>>() {
    public int compare(Pair<String, String> pair, Pair<String, String> pair1) {
      return pair.getFirst().compareTo(pair1.getFirst());
    }
  };
  private final String[] myUndefinedMacroNames;

  public PathMacroTable() {
    this(ArrayUtil.EMPTY_STRING_ARRAY, false);
  }

  public PathMacroTable(String[] undefinedMacroNames, boolean editOnlyPathsMode) {
    myEditOnlyPaths = editOnlyPathsMode;
    myUndefinedMacroNames = undefinedMacroNames;
    setModel(myTableModel);
    TableColumn column = getColumnModel().getColumn(NAME_COLUMN);
    column.setCellRenderer(new DefaultTableCellRenderer() {
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        final String macroValue = getMacroValueAt(row);
        component.setForeground(macroValue.length() == 0
                                ? Color.RED
                                : isSelected ? table.getSelectionForeground() : table.getForeground());
        return component;
      }
    });
    setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    //obtainData();
  }

  public String getMacroValueAt(int row) {
    return (String)getValueAt(row, VALUE_COLUMN);
  }

  public String getMacroNameAt(int row) {
    return (String)getValueAt(row, NAME_COLUMN);
  }

  public void addMacro() {
    final String title = ApplicationBundle.message("title.add.variable");
    final PathMacroEditor macroEditor = new PathMacroEditor(title, "", "", new AddValidator(title));
    macroEditor.show();
    if (macroEditor.isOK()) {
      final String name = macroEditor.getName();
      myMacros.add(new Pair<String,String>(name, macroEditor.getValue()));
      Collections.sort(myMacros, MACRO_COMPARATOR);
      final int index = indexOfMacroWithName(name);
      LOG.assertTrue(index >= 0);
      myTableModel.fireTableDataChanged();
      setRowSelectionInterval(index, index);
    }
  }

  public boolean isAddEnabled() {
    return !myEditOnlyPaths;
  }

  public boolean isEditEnabled() {
    return getValidSelectionRowsCount() == 1;
  }

  public boolean isRemoveEnabled() {
    return !myEditOnlyPaths && getValidSelectionRowsCount() > 0;
  }

  private int getValidSelectionRowsCount() {
    final int[] selectedRows = getSelectedRows();
    int count = 0;
    for (int selectedRow : selectedRows) {
      if (isValidRow(selectedRow)) {
        count++;
      }
    }
    return count;
  }

  private boolean isValidRow(int selectedRow) {
    return selectedRow >= 0 && selectedRow < myMacros.size();
  }

  public void removeSelectedMacros() {
    final int[] selectedRows = getSelectedRows();
    if(selectedRows.length == 0) return;
    Arrays.sort(selectedRows);
    final int originalRow = selectedRows[0];
    for (int i = selectedRows.length - 1; i >= 0; i--) {
      final int selectedRow = selectedRows[i];
      if (isValidRow(selectedRow)) {
        myMacros.remove(selectedRow);
      }
    }
    myTableModel.fireTableDataChanged();
    if (originalRow < getRowCount()) {
      setRowSelectionInterval(originalRow, originalRow);
    } else if (getRowCount() > 0) {
      final int index = getRowCount() - 1;
      setRowSelectionInterval(index, index);
    }
  }

  public void commit() {
    myPathMacros.removeAllMacros();
    for (Pair<String, String> pair : myMacros) {
      myPathMacros.setMacro(pair.getFirst(), pair.getSecond().replace(File.separatorChar, '/'));
    }
  }

  public void reset() {
    obtainData();
  }

  private boolean hasMacroWithName(String name) {
    for (Pair<String, String> macro : myMacros) {
      if (name.equals(macro.getFirst())) {
        return true;
      }
    }
    return false;
  }

  private int indexOfMacroWithName(String name) {
    for (int i = 0; i < myMacros.size(); i++) {
      final Pair<String, String> pair = myMacros.get(i);
      if (name.equals(pair.getFirst())) {
        return i;
      }
    }
    return -1;
  }

  private void obtainData() {
    obtainMacroPairs(myMacros);
    myTableModel.fireTableDataChanged();
  }

  private void obtainMacroPairs(final List<Pair<String, String>> macros) {
    macros.clear();
    final Set<String> macroNames = myPathMacros.getUserMacroNames();
    for (String name : macroNames) {
      macros.add(Pair.create(name, myPathMacros.getValue(name).replace('/', File.separatorChar)));
    }
    for (String undefinedMacroName : myUndefinedMacroNames) {
      macros.add(new Pair<String, String>(undefinedMacroName, ""));
    }
    Collections.sort(macros, MACRO_COMPARATOR);
  }

  public void editMacro() {
    if (getSelectedRowCount() != 1) {
      return;
    }
    final int selectedRow = getSelectedRow();
    final Pair<String, String> pair = myMacros.get(selectedRow);
    final String title = ApplicationBundle.message("title.edit.variable");
    final String macroName = pair.getFirst();
    final PathMacroEditor macroEditor = new PathMacroEditor(title, macroName, pair.getSecond(), new EditValidator());
    macroEditor.setMacroNameEditable(!myEditOnlyPaths);
    macroEditor.show();
    if (macroEditor.isOK()) {
      myMacros.remove(selectedRow);
      myMacros.add(Pair.create(macroEditor.getName(), macroEditor.getValue()));
      Collections.sort(myMacros, MACRO_COMPARATOR);
      myTableModel.fireTableDataChanged();
    }
  }

  public boolean isModified() {
    final ArrayList<Pair<String,String>> macros = new ArrayList<Pair<String,String>>();
    obtainMacroPairs(macros);
    return !macros.equals(myMacros);
  }

  private class MyTableModel extends AbstractTableModel{
    public int getColumnCount() {
      return 2;
    }

    public int getRowCount() {
      return myMacros.size();
    }

    public Class getColumnClass(int columnIndex) {
      return String.class;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      final Pair<String, String> pair = myMacros.get(rowIndex);
      switch (columnIndex) {
        case NAME_COLUMN: return pair.getFirst();
        case VALUE_COLUMN: return pair.getSecond();
      }
      LOG.error("Wrong indices");
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    }

    public String getColumnName(int columnIndex) {
      switch (columnIndex) {
        case NAME_COLUMN: return ApplicationBundle.message("column.name");
        case VALUE_COLUMN: return ApplicationBundle.message("column.value");
      }
      return null;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }
  }

  private class AddValidator implements PathMacroEditor.Validator {
    private final String myTitle;

    public AddValidator(String title) {
      myTitle = title;
    }

    public boolean checkName(String name) {
      return name.length() > 0 && name.indexOf('$') < 0;
    }

    public boolean isOK(String name, String value) {
      if(name.length() == 0) return false;
      if (hasMacroWithName(name)) {
        Messages.showErrorDialog(PathMacroTable.this,
                                 ApplicationBundle.message("error.variable.already.exists", name), myTitle);
        return false;
      }
      return true;
    }
  }

  private static class EditValidator implements PathMacroEditor.Validator {
    public boolean checkName(String name) {
      return name.length() > 0 && name.indexOf('$') < 0;
    }

    public boolean isOK(String name, String value) {
      return checkName(name);
    }
  }
}
