/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.actions.view.array;

import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/*
*	Use a JTable as a renderer for row numbers of a given main table.
*  This table must be added to the row header of the scrollpane that
*  contains the main table.
*/
public class RowNumberTable extends JBTable
  implements ChangeListener, PropertyChangeListener, TableModelListener {
  private JTable main;

  public RowNumberTable(JTable table) {
    main = table;
    main.addPropertyChangeListener(this);
    main.getModel().addTableModelListener(this);

    setFocusable(false);
    setAutoCreateColumnsFromModel(false);
    setSelectionModel(main.getSelectionModel());


    TableColumn column = new TableColumn();
    column.setHeaderValue(" ");
    addColumn(column);
    column.setCellRenderer(new RowNumberRenderer());

    getColumnModel().getColumn(0).setPreferredWidth(50);
    setPreferredScrollableViewportSize(getPreferredSize());
  }

  @Override
  public void addNotify() {
    super.addNotify();

    Component c = getParent();

    //  Keep scrolling of the row table in sync with the main table.

    if (c instanceof JViewport) {
      JViewport viewport = (JViewport)c;
      viewport.addChangeListener(this);
    }
  }

  /*
   *  Delegate method to main table
   */
  @Override
  public int getRowCount() {
    return main.getRowCount();
  }

  @Override
  public int getRowHeight(int row) {
    int rowHeight = main.getRowHeight(row);

    if (rowHeight != super.getRowHeight(row)) {
      super.setRowHeight(row, rowHeight);
    }

    return rowHeight;
  }

  /*
   *  No model is being used for this table so just use the row number
   *  as the value of the cell.
   */
  @Override
  public Object getValueAt(int row, int column) {
    return Integer.toString(row + 1);
  }

  /*
   *  Don't edit data in the main TableModel by mistake
   */
  @Override
  public boolean isCellEditable(int row, int column) {
    return false;
  }

  /*
   *  Do nothing since the table ignores the model
   */
  @Override
  public void setValueAt(Object value, int row, int column) {
  }

  //
  //  Implement the ChangeListener
  //
  public void stateChanged(ChangeEvent e) {
    //  Keep the scrolling of the row table in sync with main table

    JViewport viewport = (JViewport)e.getSource();
    JScrollPane scrollPane = (JScrollPane)viewport.getParent();
    scrollPane.getVerticalScrollBar().setValue(viewport.getViewPosition().y);
  }

  //
  //  Implement the PropertyChangeListener
  //
  public void propertyChange(PropertyChangeEvent e) {
    //  Keep the row table in sync with the main table

    if ("selectionModel".equals(e.getPropertyName())) {
      setSelectionModel(main.getSelectionModel());
    }

    if ("rowHeight".equals(e.getPropertyName())) {
      repaint();
    }

    if ("model".equals(e.getPropertyName())) {
      main.getModel().addTableModelListener(this);
      revalidate();
    }
  }

  //
  //  Implement the TableModelListener
  //
  @Override
  public void tableChanged(TableModelEvent e) {
    revalidate();
  }

  /*
   *  Attempt to mimic the table header renderer
   */
  private class RowNumberRenderer extends DefaultTableCellRenderer {
    public RowNumberRenderer() {
      setHorizontalAlignment(JLabel.CENTER);
    }

    public Component getTableCellRendererComponent(
      JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (table != null) {
        JTableHeader header = table.getTableHeader();

        if (header != null) {
          setForeground(header.getForeground());
          setBackground(header.getBackground());
          setFont(header.getFont());
        }
      }

      if (isSelected) {
        setFont(getFont().deriveFont(Font.BOLD));
      }

      setText((value == null) ? "" : value.toString());
      setBorder(UIManager.getBorder("TableHeader.cellBorder"));

      return this;
    }
  }
}
