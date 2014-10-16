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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class RowHeaderTable extends JBTable implements PropertyChangeListener, TableModelListener {
  private JTable main;
  private int rowShift = 0;

  public RowHeaderTable(JTable table) {
    main = table;
    main.getModel().addTableModelListener(this);

    setFocusable(false);
    setAutoCreateColumnsFromModel(false);
    setSelectionModel(main.getSelectionModel());
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    TableColumn column = new TableColumn();
    column.setHeaderValue(" ");
    addColumn(column);
    column.setCellRenderer(new RowNumberRenderer());

    getColumnModel().getColumn(0).setPreferredWidth(50);
    setPreferredScrollableViewportSize(getPreferredSize());
    setRowHeight(main.getRowHeight());
    MouseListener[] listeners = getMouseListeners();
    for (MouseListener l : listeners) {
      removeMouseListener(l);
    }
  }

  @Override
  protected void paintComponent(@NotNull Graphics g) {
    getEmptyText().setText("");
    super.paintComponent(g);
  }

  @Override
  public int getRowCount() {
    return main.getRowCount();
  }

  @Override
  public int getRowHeight(int row) {
    setRowHeight(main.getRowHeight());
    return super.getRowHeight(row);
  }

  @Override
  public Object getValueAt(int row, int column) {
    return Integer.toString(row + rowShift);
  }

  public void setRowShift(int shift) {
    rowShift = shift;
  }

  public int getRowShift() {
    return rowShift;
  }

  @Override
  public boolean isCellEditable(int row, int column) {
    return false;
  }


  @Override
  public void setValueAt(Object value, int row, int column) {
  }

  public void propertyChange(PropertyChangeEvent e) {
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

  @Override
  public void tableChanged(TableModelEvent e) {
    revalidate();
  }

  private class RowNumberRenderer extends DefaultTableCellRenderer {
    public RowNumberRenderer() {
      setHorizontalAlignment(SwingConstants.CENTER);
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
