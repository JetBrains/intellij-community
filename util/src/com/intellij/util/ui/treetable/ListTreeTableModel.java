package com.intellij.util.ui.treetable;

import com.intellij.util.ui.ColumnInfo;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

/**
 * author: lesya
 */
public class ListTreeTableModel extends DefaultTreeModel implements TreeTableModel{

  private final ColumnInfo[] myColumns;

  public ListTreeTableModel(TreeNode root, ColumnInfo[] columns) {
    super(root);
    myColumns = columns;
  }

  public int getColumnCount() {
    return myColumns.length;
  }

  public String getColumnName(int column) {
    return myColumns[column].getName();
  }

  public Object getValueAt(Object node, int column) {
    return myColumns[column].valueOf(node);
  }

  public int getChildCount(Object parent) {
    return ((TreeNode)parent).getChildCount();
  }

  public Object getChild(Object parent, int index) {
    return ((TreeNode)parent).getChildAt(index);
  }

  public Class getColumnClass(int column) {
    return myColumns[column].getColumnClass();
  }

  public boolean isCellEditable(Object node, int column) {
    return myColumns[column].isCellEditable(node);
  }

  public void setValueAt(Object aValue, Object node, int column) {
    myColumns[column].setValue(node, aValue);
  }

}
