/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;

/**
 * User: anna
 * Date: 02-Dec-2005
 */
public class StripedTreeTable extends TreeTable{
  private Color myStripeColor1;
  private Color myStripeColor2;

  public StripedTreeTable(final TreeTableModel treeTableModel, Color stripeColor1) {
    this(treeTableModel, stripeColor1, Color.white);
  }

  public StripedTreeTable(final TreeTableModel treeTableModel, Color stripeColor1, Color stripeColor2) {
    super(treeTableModel);
    myStripeColor1 = stripeColor1;
    myStripeColor2 = stripeColor2;
    setOpaque(false);
    getTree().setOpaque(false);
    getTree().getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        final int[] selectionRows = getTree().getSelectionRows();
        if (selectionRows != null && selectionRows.length > 0) {
          addColumnSelectionInterval(0, getColumnCount() - 1);
        }
      }
    });
    for(int i = 0; i < getColumnCount(); i++){
      final TableColumn column = getColumn(getColumnName(i));
      final TableCellRenderer cellRenderer = getCellRenderer(i);
      if (cellRenderer != null) {
        column.setCellRenderer(new TableCellRenderer() {
          public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            Component component = cellRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            drawStripe(isSelected, component, row, UIUtil.getTableSelectionBackground(), UIUtil.getTableBackground());
            return component;
          }
        });
      }
    }
    final TreeCellRenderer treeRenderer = getTreeRenderer();
    if (treeRenderer != null) {
      getTree().setCellRenderer(new DefaultTreeCellRenderer(){
        public Component getTreeCellRendererComponent(JTree tree,
                                                      Object value,
                                                      boolean selected,
                                                      boolean expanded,
                                                      boolean leaf,
                                                      int row,
                                                      boolean hasFocus) {
          final Component component = treeRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
          drawStripe(selected, component, row, UIUtil.getTreeSelectionBackground(), UIUtil.getTreeTextBackground());
          return component;
        }
      });
    }
  }



  protected TableCellRenderer getCellRenderer(int column){
    return null;
  }

  protected TreeCellRenderer getTreeRenderer(){
    return null;
  }

  public void paint(Graphics g) {
    final Color color = g.getColor();
    for (int i = 0; i < getRowCount(); i++){
      if (getSelectedRow() == i){
        drawStripe(g, i, UIUtil.getTableSelectionBackground());
      } else if (i % 2 == 0){
        drawStripe(g, i, myStripeColor1);
      } else {
        drawStripe(g, i, myStripeColor2);
      }
    }
    g.setColor(color);
    super.paint(g);
  }

  private void drawStripe(final Graphics g, final int i, final Color color) {
    g.setColor(color);
    final Rectangle cellRect = getCellRect(i, 0, true);
    g.fillRect(cellRect.x, cellRect.y, cellRect.width, cellRect.height);
  }

  private void drawStripe(final boolean isSelected,
                          final Component component,
                          final int row,
                          final Color selectionColor,
                          final Color bgColor) {
    if (isSelected){
      component.setBackground(selectionColor);
    } else {
      if (row % 2 == 0) {
        component.setBackground(myStripeColor1);
      } else {
        component.setBackground(bgColor);
      }
    }
  }


}
