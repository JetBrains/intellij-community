/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.treetable;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.*;

public class ListTreeTableModelOnColumns extends DefaultTreeModel
  implements TreeTableModel, SortableColumnModel{

  private ColumnInfo[] myColumns;

  public ListTreeTableModelOnColumns(TreeNode root, ColumnInfo[] columns) {
    super(root);
    myColumns = columns;
  }

  public int getColumnCount() {
    return myColumns.length;
  }

  public String getColumnName(int column) {
    return myColumns[column].getName();
  }

  public Object getValueAt(Object value, int column) {
    return myColumns[column].valueOf(value);
  }

  public Object getChild(Object parent, int index) {
    return ((TreeNode) parent).getChildAt(index);
  }

  public int getChildCount(Object parent) {
    return ((TreeNode) parent).getChildCount();
  }

  public Class getColumnClass(int column) {
    return myColumns[column].getColumnClass();
  }

  public ColumnInfo[] getColumns() {
    return myColumns;
  }

  public boolean isCellEditable(Object node, int column) {
    return myColumns[column].isCellEditable(node);
  }

  public void setValueAt(Object aValue, Object node, int column) {
    myColumns[column].setValue(node, aValue);
  }

  /**
   * @param columns
   * @return true if changed
   */
  public boolean setColumns(ColumnInfo[] columns) {
    if (myColumns != null && Arrays.equals(myColumns, columns)) {
      return false;
    }
    myColumns = columns;
    return true;
  }

  public ColumnInfo[] getColumnInfos() {
    return myColumns;
  }

  public List getItems() {
    ArrayList result = new ArrayList();
    TreeNode root = (TreeNode) getRoot();
    for (int i = 0; i < root.getChildCount(); i++) {
      addElementsToCollection(root.getChildAt(i), result);
    }
    return result;
  }

  private void addElementsToCollection(TreeNode parent, Collection collection) {
    collection.add(parent);
    Enumeration children = parent.children();
    if (children == null) return;
    while(children.hasMoreElements()){
      TreeNode child = (TreeNode) children.nextElement();
      addElementsToCollection(child, collection);
    }
  }

  public void sortByColumn(int columnIndex) {
  }

  public void sortByColumn(int columnIndex, int sortingType) {
  }

  public int getSortedColumnIndex() {
    return -1;
  }

  public int getSortingType() {
    return -1;
  }

  public void setSortable(boolean aBoolean) {
  }

  public boolean isSortable() {
    return false;
  }
}
