package com.intellij.util.ui.treetable;

import com.intellij.util.ui.treetable.TreeTable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 * A TreeCellRenderer that displays a JTree.
 */
public class TreeTableCellRenderer implements TableCellRenderer {
  private TreeTable myTreeTable;
  private final TreeTableTree myTree;
  private TreeCellRenderer myTreeCellRenderer;
  private Border myDefaultBorder = UIManager.getBorder("Table.focusCellHighlightBorder");


  public TreeTableCellRenderer(TreeTable treeTable, TreeTableTree tree) {
    myTreeTable = treeTable;
    myTree = tree;
  }

  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (myTreeCellRenderer != null)
      myTree.setCellRenderer(myTreeCellRenderer);
    if (isSelected){
      myTree.setBackground(table.getSelectionBackground());
    }
    else{
      myTree.setBackground(table.getBackground());
    }
    TableModel model = myTreeTable.getModel();
    myTree.setTreeTableTreeBorder(hasFocus && model.getColumnClass(column).equals(TreeTableModel.class) ? myDefaultBorder : null);
    myTree.setVisibleRow(row);
    return myTree;
  }

  public void setCellRenderer(TreeCellRenderer treeCellRenderer) {
    myTreeCellRenderer = treeCellRenderer;
  }
  public void setDefaultBorder(Border border) {
    myDefaultBorder = border;
  }
  public void putClientProperty(String s, String s1) {
    myTree.putClientProperty(s, s1);
  }

  public void setRootVisible(boolean b) {
    myTree.setRootVisible(b);
  }

  public void setShowsRootHandles(boolean b) {
    myTree.setShowsRootHandles(b);
  }

}
