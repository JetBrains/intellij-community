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
package com.intellij.ui;

import com.intellij.util.ui.ItemRemovable;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public class TableUtil {
  public interface ItemChecker {
    boolean isOperationApplyable(TableModel model, int row);
  }

  public static List<Object[]> removeSelectedItems(JTable table) {
    return removeSelectedItems(table, null);
  }

  public static void selectRows(JTable table, int[] rows) {
    ListSelectionModel selectionModel = table.getSelectionModel();
    selectionModel.clearSelection();
    int count = table.getRowCount();
    for (int row : rows) {
      if (row >= 0 && row < count) {
        selectionModel.addSelectionInterval(row, row);
      }
    }
  }

  public static void scrollSelectionToVisible(JTable table){
    ListSelectionModel selectionModel = table.getSelectionModel();
    int maxSelectionIndex = selectionModel.getMaxSelectionIndex();
    int minSelectionIndex = selectionModel.getMinSelectionIndex();
    if(maxSelectionIndex == -1){
      return;
    }
    Rectangle minCellRect = table.getCellRect(minSelectionIndex, 0, false);
    Rectangle maxCellRect = table.getCellRect(maxSelectionIndex, 0, false);
    Point selectPoint = minCellRect.getLocation();
    int allHeight = maxCellRect.y + maxCellRect.height - minCellRect.y;
    allHeight = Math.min(allHeight, table.getVisibleRect().height);
    table.scrollRectToVisible(new Rectangle(selectPoint, new Dimension(1,allHeight)));
  }

  public static List<Object[]> removeSelectedItems(JTable table, ItemChecker applyable) {
    if (table.isEditing()){
      table.getCellEditor().stopCellEditing();
    }
    TableModel model = table.getModel();
    if (!(model instanceof ItemRemovable)) {
      throw new RuntimeException("model must be instance of ItemRemovable");
    }

    ListSelectionModel selectionModel = table.getSelectionModel();
    int minSelectionIndex = selectionModel.getMinSelectionIndex();
    if (minSelectionIndex == -1) return new ArrayList<Object[]>(0);

    List<Object[]> removedItems = new LinkedList<Object[]>();

    final int columnCount = model.getColumnCount();
    for (int idx = table.getRowCount() - 1; idx >= 0; idx--) {
      if (selectionModel.isSelectedIndex(idx) && (applyable == null || applyable.isOperationApplyable(model, idx))) {
        final Object[] row = new Object[columnCount];
        for(int column = 0; column < columnCount; column++){
          row[column] = model.getValueAt(idx, column);
        }
        removedItems.add(0, row);
        ((ItemRemovable)model).removeRow(idx);
      }
    }
    int count = model.getRowCount();
    if (count == 0) {
      table.clearSelection();
    }
    else {
      final int selectionIndex = selectionModel.getMinSelectionIndex();
      if (selectionIndex < 0 || selectionIndex >= count) {
        // if nothing remains selected, set selected row
        int rowToSelect = Math.min(count -1, selectionIndex);
        selectionModel.setSelectionInterval(rowToSelect, rowToSelect);
      }
    }
    return removedItems;
  }

  public static int moveSelectedItemsUp(JTable table) {
    if (table.isEditing()){
      table.getCellEditor().stopCellEditing();
    }
    TableModel model = table.getModel();
    ListSelectionModel selectionModel = table.getSelectionModel();
    int counter = 0;
    for(int row = 0; row < model.getRowCount(); row++){
      if (selectionModel.isSelectedIndex(row)) {
        counter++;
        for (int column = 0; column < model.getColumnCount(); column++) {
          Object temp = model.getValueAt(row, column);
          model.setValueAt(model.getValueAt(row - 1, column), row, column);
          model.setValueAt(temp, row - 1, column);
        }
        selectionModel.removeSelectionInterval(row, row);
        selectionModel.addSelectionInterval(row - 1, row - 1);
      }
    }
    Rectangle cellRect = table.getCellRect(selectionModel.getMinSelectionIndex(), 0, true);
    if (cellRect != null) {
      table.scrollRectToVisible(cellRect);
    }
    table.repaint();
    return counter;
  }

  public static int moveSelectedItemsDown(JTable table) {
    if (table.isEditing()){
      table.getCellEditor().stopCellEditing();
    }
    TableModel model = table.getModel();
    ListSelectionModel selectionModel = table.getSelectionModel();
    int counter = 0;
    for(int row = model.getRowCount() - 1; row >= 0 ; row--){
      if (selectionModel.isSelectedIndex(row)) {
        counter++;
        for (int column = 0; column < model.getColumnCount(); column++) {
          Object temp = model.getValueAt(row, column);
          model.setValueAt(model.getValueAt(row + 1, column), row, column);
          model.setValueAt(temp, row + 1, column);
        }
        selectionModel.removeSelectionInterval(row, row);
        selectionModel.addSelectionInterval(row + 1, row + 1);
      }
    }
    Rectangle cellRect = table.getCellRect(selectionModel.getMaxSelectionIndex(), 0, true);
    if (cellRect != null) {
      table.scrollRectToVisible(cellRect);
    }
    table.repaint();
    return counter;
  }

  public static void editCellAt(final JTable table, int row, int column) {
    if (table.editCellAt(row, column)) {
      final Component component = table.getEditorComponent();
      if (component != null) {
        component.requestFocus();
      }
    }
  }

  public static void stopEditing(JTable table) {
    if (table.isEditing()) {
      final TableCellEditor cellEditor = table.getCellEditor();
      if (cellEditor != null) {
        cellEditor.stopCellEditing();
      }
      int row = table.getSelectedRow();
      int column = table.getSelectedColumn();
      if (row >= 0 && column >= 0) {
        TableCellEditor editor = table.getCellEditor(row, column);
        if (editor != null) {
          editor.stopCellEditing();
          //Object value = editor.getCellEditorValue();
          //
          //table.setValueAt(value, row, column);
        }
      }
    }
  }

  public static void ensureSelectionExists(JTable table) {
    if (table.getSelectedRow() != -1 || table.getRowCount() == 0) return;
    table.setRowSelectionInterval(0, 0);
  }
}