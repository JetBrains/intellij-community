package com.intellij.ide.plugins;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 4:16:50 PM
 * To change this template use Options | File Templates.
 */
abstract public class PluginTableModel <T> extends AbstractTableModel implements SortableColumnModel {
  protected PluginManagerColumnInfo [] columns;
  protected SortableProvider sortableProvider;
  protected List<T> view;

  public PluginTableModel(PluginManagerColumnInfo[] columns, SortableProvider sortableProvider) {
    this.columns = columns;
    this.sortableProvider = sortableProvider;
  }

  public int getColumnCount() {
    return columns.length;
  }

  public ColumnInfo[] getColumnInfos() {
    return columns;
  }

  public boolean isSortable() {
    return true;
  }

  public void setSortable(boolean aBoolean) {
    // do nothing cause it's always sortable
  }

  public String getColumnName(int column) {
    return columns[column].getName();
  }

  public int getSortedColumnIndex() {
    return sortableProvider.getSortColumn();
  }

  public int getSortingType() {
    return sortableProvider.getSortOrder();
  }

  public T getObjectAt (int row) {
    return (T)view.get(row);
  }

  public int getRowCount() {
    return view.size();
  }

  public Object getValueAt(int rowIndex, int columnIndex) {
    return columns[columnIndex].valueOf(getObjectAt(rowIndex));
  }

  public void sortByColumn(int columnIndex) {
    Collections.sort(view, columns[columnIndex].getComparator());
    fireTableDataChanged();
  }
}
