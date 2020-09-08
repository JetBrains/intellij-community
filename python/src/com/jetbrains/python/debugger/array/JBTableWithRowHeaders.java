// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.array;

import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;

/**
 * @author amarch
 */
public class JBTableWithRowHeaders extends AbstractDataViewTable {
  private static final int MAX_INIT_COLUMN_WIDTH = 250;
  private final JBScrollPane myScrollPane;
  private boolean myAutoResize;
  private final RowHeaderTable myRowHeaderTable;
  private final Set<Integer> myNotAdjustableColumns = new HashSet<>();

  public JBTableWithRowHeaders(boolean autoResize) {
    myAutoResize = autoResize;
    setAutoResizeMode(myAutoResize ? AUTO_RESIZE_OFF : AUTO_RESIZE_SUBSEQUENT_COLUMNS);
    setRowSelectionAllowed(false);
    setMaxItemsForSizeCalculation(50);
    setTableHeader(new CustomTableHeader(this));
    getTableHeader().setDefaultRenderer(new ColumnHeaderRenderer());
    getTableHeader().setReorderingAllowed(false);

    myScrollPane = new JBScrollPane(this);
    myRowHeaderTable = new JBTableWithRowHeaders.RowHeaderTable(this);
    myRowHeaderTable.getEmptyText().setText("");
    myScrollPane.setRowHeaderView(myRowHeaderTable);
    myScrollPane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, myRowHeaderTable.getTableHeader()); //NON-NLS
  }

  @NotNull
  @Override
  public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
    Component component = super.prepareRenderer(renderer, row, column);
    JTableHeader header = getTableHeader();
    TableColumn resizingColumn = header.getResizingColumn();
    if (myAutoResize && resizingColumn == null && !myNotAdjustableColumns.contains(column)) {
      updateColumnWidth(column, component.getPreferredSize().width, this);
    }
    return component;
  }

  @Override
  public void setAutoResize(boolean autoResize) {
    myNotAdjustableColumns.clear();
    myAutoResize = autoResize;
    setAutoResizeMode(myAutoResize ? AUTO_RESIZE_OFF : AUTO_RESIZE_SUBSEQUENT_COLUMNS);
  }

  @Override
  public void setEmpty() {
    setModel(new DefaultTableModel());
    myRowHeaderTable.setModel(new DefaultTableModel());
  }

  private static int updateColumnWidth(int column, int width, @NotNull JTable table) {
    TableColumn tableColumn = table.getColumnModel().getColumn(column);
    int headerWidth = new ColumnHeaderRenderer().getTableCellRendererComponent(table, tableColumn.getHeaderValue(), false, false, -1, column).getPreferredSize().width + 4;
    int newWidth = Math.max(width, headerWidth) + 2 * table.getIntercellSpacing().width;
    tableColumn.setPreferredWidth(Math.min(Math.max(newWidth, tableColumn.getPreferredWidth()), MAX_INIT_COLUMN_WIDTH));
    return newWidth;
  }

  @Override
  public JBScrollPane getScrollPane() {
    return myScrollPane;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return getPreferredSize().width < getParent().getWidth();
  }


  @Override
  public void setModel(@NotNull TableModel model) {

    super.setModel(model);
    if (model instanceof AsyncArrayTableModel) {
      myRowHeaderTable.setModel(((AsyncArrayTableModel)model).getRowHeaderModel());
    }
  }

  public class RowHeaderTable extends JBTable implements PropertyChangeListener, TableModelListener {
    private final JTable myMainTable;

    public RowHeaderTable(JTable table) {
      myMainTable = table;
      setFocusable(false);
      setSelectionModel(myMainTable.getSelectionModel());
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      setRowHeight(myMainTable.getRowHeight());
      MouseListener[] listeners = getMouseListeners();
      for (MouseListener l : listeners) {
        removeMouseListener(l);
      }
      setModel(new DefaultTableModel(0, 1));
      setPreferredScrollableViewportSize(getPreferredSize());
    }

    @NotNull
    @Override
    public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
      Component component = super.prepareRenderer(renderer, row, column);
      if (myAutoResize) {
        getPreferredSize().width = updateColumnWidth(column, component.getPreferredSize().width, this);
      }
      else {
        getColumnModel().getColumn(0).setPreferredWidth(50);
      }
      setPreferredScrollableViewportSize(getPreferredSize());
      return component;
    }

    @Override
    public void setModel(@NotNull TableModel model) {
      setAutoCreateColumnsFromModel(true);
      super.setModel(model);
      if (getColumnModel().getColumnCount() > 0) {
        getColumnModel().getColumn(0).setCellRenderer(new RowNumberRenderer());
      }
    }

    @Override
    public int getRowHeight(int row) {
      int height = super.getRowHeight();
      if (height != myMainTable.getRowHeight()) {
        setRowHeight(myMainTable.getRowHeight());
      }
      return super.getRowHeight(row);
    }


    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }


    @Override
    public void propertyChange(PropertyChangeEvent e) {
      if ("selectionModel".equals(e.getPropertyName())) {
        setSelectionModel(myMainTable.getSelectionModel());
      }

      if ("rowHeight".equals(e.getPropertyName())) {
        repaint();
      }

      if ("model".equals(e.getPropertyName())) {
        revalidate();
      }
    }


    private class RowNumberRenderer extends DefaultTableCellRenderer {
      RowNumberRenderer() {
        setHorizontalAlignment(SwingConstants.CENTER);
      }

      @Override
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

        setText((value == null) ? "" : value.toString()); //NON-NLS
        setBorder(UIManager.getBorder("TableHeader.cellBorder"));

        return this;
      }
    }
  }

  public class CustomTableHeader extends JBTableHeader {

    public CustomTableHeader(JTable table) {
      super();
      setColumnModel(table.getColumnModel());
      table.getColumnModel().getSelectionModel().addListSelectionListener(e -> repaint());
    }

    @Override
    public void columnSelectionChanged(ListSelectionEvent e) {
      repaint();
    }

    @Override
    public void setResizingColumn(TableColumn column) {
      super.setResizingColumn(column);
      if (column != null) {
        JBTableWithRowHeaders.this.myNotAdjustableColumns.add(column.getModelIndex());
      }
    }
  }

  public static class ColumnHeaderRenderer extends DefaultTableHeaderCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
      super.getTableCellRendererComponent(table, value, selected, focused, row, column);
      int selectedColumn = table.getSelectedColumn();
      if (selectedColumn == column) {
        setFont(getFont().deriveFont(Font.BOLD));
      }
      return this;
    }
  }

  public static class DefaultTableHeaderCellRenderer extends DefaultTableCellRenderer {

    public DefaultTableHeaderCellRenderer() {
      setHorizontalAlignment(CENTER);
      setHorizontalTextPosition(LEFT);
      setVerticalAlignment(BOTTOM);
      setOpaque(false);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
      super.getTableCellRendererComponent(table, value,
                                          isSelected, hasFocus, row, column);
      JTableHeader tableHeader = table.getTableHeader();
      if (tableHeader != null) {
        setForeground(tableHeader.getForeground());
      }
      setBorder(UIManager.getBorder("TableHeader.cellBorder"));
      return this;
    }
  }
}
