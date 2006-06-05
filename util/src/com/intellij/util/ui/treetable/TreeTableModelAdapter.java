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

import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.TreePath;

/**
 * This is a wrapper class takes a TreeTableModel and implements
 * the table model interface. The implementation is trivial, with
 * all of the event dispatching support provided by the superclass:
 * the AbstractTableModel.
 *
 * @version 1.2 10/27/98
 *
 * @author Philip Milne
 * @author Scott Violet
 */
public class TreeTableModelAdapter extends AbstractTableModel {
  JTree tree;
  TreeTableModel treeTableModel;
  private final Table table;

  public TreeTableModelAdapter(TreeTableModel treeTableModel, JTree tree, Table table) {
    this.tree = tree;
    this.treeTableModel = treeTableModel;
    this.table = table;

    tree.addTreeExpansionListener(new TreeExpansionListener() {
      // Don't use fireTableRowsInserted() here; the selection model
      // would get updated twice.
      public void treeExpanded(TreeExpansionEvent event) {
        fireTableDataChanged();
      }

      public void treeCollapsed(TreeExpansionEvent event) {
        fireTableDataChanged();
      }
    });

    // Install a TreeModelListener that can update the table when
    // tree changes. We use delayedFireTableDataChanged as we can
    // not be guaranteed the tree will have finished processing
    // the event before us.
    treeTableModel.addTreeModelListener(new TreeModelListener() {
      public void treeNodesChanged(TreeModelEvent e) {
        delayedFireTableDataChanged();
      }

      public void treeNodesInserted(TreeModelEvent e) {
        delayedFireTableDataChanged();
      }

      public void treeNodesRemoved(TreeModelEvent e) {
        delayedFireTableDataChanged();
      }

      public void treeStructureChanged(TreeModelEvent e) {
        delayedFireTableDataChanged();
      }
    });
  }

  // Wrappers, implementing TableModel interface.

  public int getColumnCount() {
    return treeTableModel.getColumnCount();
  }

  public String getColumnName(int column) {
    return treeTableModel.getColumnName(column);
  }

  public Class getColumnClass(int column) {
    return treeTableModel.getColumnClass(column);
  }

  public int getRowCount() {
    return tree.getRowCount();
  }

  protected Object nodeForRow(int row) {
    TreePath treePath = tree.getPathForRow(row);
    return treePath == null ? null : treePath.getLastPathComponent();
  }

  public Object getValueAt(int row, int column) {
    final Object o = nodeForRow(row);
    return o == null? null : treeTableModel.getValueAt(o, column);
  }

  public boolean isCellEditable(int row, int column) {
    final Object o = nodeForRow(row);
    return o == null? false : treeTableModel.isCellEditable(o, column);
  }

  public void setValueAt(Object value, int row, int column) {
    final Object o = nodeForRow(row);
    if (o != null) treeTableModel.setValueAt(value, o, column);
  }

  /**
   * Invokes fireTableDataChanged after all the pending events have been
   * processed. SwingUtilities.invokeLater is used to handle this.
   */
  protected void delayedFireTableDataChanged() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        fireTableDataChanged();
      }
    });
  }

  public void fireTableDataChanged() {
    // have to restore table selection since AbstractDataModel.fireTableDataChanged() clears all selection
    final int[] selectedRows = table.getSelectedRows();
    super.fireTableDataChanged();
    for(int i=0;i<selectedRows.length;i++){
      int selectedRow=selectedRows[i];
      table.getSelectionModel().addSelectionInterval(selectedRow,selectedRow);
    }
  }
}
