package com.intellij.util.ui.treetable;

import com.intellij.util.ui.Table;
import com.intellij.util.ui.Table;
import com.intellij.util.ui.treetable.TreeTableModel;

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
    return treeTableModel.getValueAt(nodeForRow(row), column);
  }

  public boolean isCellEditable(int row, int column) {
    return treeTableModel.isCellEditable(nodeForRow(row), column);
  }

  public void setValueAt(Object value, int row, int column) {
    treeTableModel.setValueAt(value, nodeForRow(row), column);
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
