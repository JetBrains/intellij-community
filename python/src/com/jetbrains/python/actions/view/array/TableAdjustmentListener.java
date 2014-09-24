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

import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

/**
 * @author amarch
 */
public class TableAdjustmentListener implements AdjustmentListener {
  private JBTable myTable;
  private int rMax;
  private int cMax;
  private int myMode;
  private int mySize;

  public static final int VERTICAL_MODE = 0;
  public static final int HORIZONTAL_MODE = 1;

  public TableAdjustmentListener(JBTable table, int rMax, int cMax, int mode, int size) {
    myTable = table;
    this.rMax = rMax;
    this.cMax = cMax;
    myMode = mode;
    mySize = size;
  }

  @Override
  public void adjustmentValueChanged(AdjustmentEvent e) {

    //reach right or bottom border
    if (e.getValue() + e.getAdjustable().getVisibleAmount() == e.getAdjustable().getMaximum()) {
      if (myMode == HORIZONTAL_MODE && myTable.getColumnCount() == mySize) {

        int x = Integer.parseInt(myTable.getColumnModel().getColumn(mySize - 1).getHeaderValue().toString()) + 1;
        int y = myTable.getRowCount();
        String repr = "val[" + y + ":, " + x + ":]";

        ArrayChunk chunk = new ArrayChunk(repr, y, 5, x, ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable().getRowShift());
        chunk.loadData();

        moveNFirstColumns(chunk.getColumns());
        loadDataIntoTable(chunk, mySize - chunk.getColumns(), 0);
      }
      else if (myMode == VERTICAL_MODE && myTable.getColumnCount() == mySize) {

        RowNumberTable rowTable = ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable();
        int x = rowTable.getRowShift() + mySize;
        int y = myTable.getColumnCount();
        String repr = "val[" + y + ":, " + x + ":]";

        ArrayChunk chunk =
          new ArrayChunk(repr, 5, y, Integer.parseInt(myTable.getColumnModel().getColumn(0).getHeaderValue().toString()), x);
        chunk.loadData();

        moveNFirstRows(chunk.getRows());
        loadDataIntoTable(chunk, 0, mySize - chunk.getRows());
      }
    }

    //reach left or upper border
    if (e.getValue() == 0) {
      if (myMode == HORIZONTAL_MODE && myTable.getColumnCount() == mySize) {

        int x = Integer.parseInt(myTable.getColumnModel().getColumn(0).getHeaderValue().toString());
        if (x != 0) {
          int y = myTable.getRowCount();
          int leftSpace = Math.min(5, x);
          String repr = "val[" + y + ":, " + (x - leftSpace) + ":]";

          ArrayChunk chunk =
            new ArrayChunk(repr, y, leftSpace, x - leftSpace, ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable().getRowShift());
          chunk.loadData();

          moveNLastColumns(chunk.getColumns());
          loadDataIntoTable(chunk, 0, 0);
        }
      }
      else if (myMode == VERTICAL_MODE && myTable.getColumnCount() == mySize) {

        RowNumberTable rowTable = ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable();
        int x = rowTable.getRowShift();
        if (x != 0) {
          int y = myTable.getColumnCount();
          int leftSpace = Math.min(5, x);
          String repr = "val[" + (x - leftSpace) + ":, " + y + ":]";

          ArrayChunk chunk = new ArrayChunk(repr, leftSpace, y,
                                            Integer.parseInt(myTable.getColumnModel().getColumn(0).getHeaderValue().toString()),
                                            x - leftSpace);
          chunk.loadData();

          moveNLastRows(chunk.getRows());
          loadDataIntoTable(chunk, 0, 0);
        }
      }
    }
  }

  private void moveNLastColumns(int n) {
    for (int j = n; j > 0; j--) {
      myTable.moveColumn(myTable.getColumnCount() - 1, 0);
      myTable.getColumnModel().getColumn(0)
        .setHeaderValue(new Integer(myTable.getColumnModel().getColumn(1).getHeaderValue().toString()) - 1);
    }
  }

  private void moveNFirstColumns(int n) {
    for (int j = 0; j < n; j++) {
      myTable.moveColumn(0, myTable.getColumnCount() - 1);
      myTable.getColumnModel().getColumn(myTable.getColumnCount() - 1)
        .setHeaderValue(new Integer(myTable.getColumnModel().getColumn(myTable.getColumnCount() - 2).getHeaderValue().toString()) + 1);
    }
  }

  private void moveNLastRows(int n) {
    for (int j = n; j > 0; j--) {
      ((DefaultTableModel)myTable.getModel()).moveRow(myTable.getRowCount() - 1, myTable.getRowCount() - 1, 0);
    }
    RowNumberTable rowTable = ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable();
    rowTable.setRowShift(rowTable.getRowShift() - n);
  }

  private void moveNFirstRows(int n) {
    for (int j = 0; j < n; j++) {
      ((DefaultTableModel)myTable.getModel()).moveRow(0, 0, myTable.getRowCount() - 1);
    }
    RowNumberTable rowTable = ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable();
    rowTable.setRowShift(rowTable.getRowShift() + n);
  }


  private void loadDataIntoTable(ArrayChunk chunk, int hShift, int vShift) {
    Object[][] data = chunk.getData();
    for (int i = 0; i < chunk.getColumns(); i++) {
      for (int j = 0; j < chunk.getRows(); j++) {
        myTable.setValueAt(data[j][i], j + vShift, i + hShift);
      }
    }
  }

  private void removeFirstNColumns(int n) {
    if (myTable.getColumnCount() == mySize + 5) {
      DefaultTableModel dtm = (DefaultTableModel)myTable.getModel();
      for (int j = 0; j < n; j++) {
        ((NumpyArrayValueProvider.MyTableModel)dtm).removeColumn(0);
      }
    }
  }

  private void removeLastNColumns(int n) {
    if (myTable.getColumnCount() == mySize + 5) {
      DefaultTableModel dtm = (DefaultTableModel)myTable.getModel();
      for (int j = 0; j < n; j++) {
        myTable.getColumnModel().removeColumn(myTable.getColumnModel().getColumn(myTable.getColumnCount() - 1));
      }
    }
  }

  private void addLastNColumns(int n) {
    DefaultTableModel dtm = (DefaultTableModel)myTable.getModel();
    int index = Integer.parseInt(myTable.getColumnModel().getColumn(mySize - 1).getHeaderValue().toString());
    for (int j = 0; j < n; j++) {
      dtm.addColumn(index + j + 1,
                    new Object[dtm.getRowCount()]);
    }
  }

  private void addFirstNColumns(int n) {
    DefaultTableModel dtm = (DefaultTableModel)myTable.getModel();
    int index = Integer.parseInt(myTable.getColumnModel().getColumn(0).getHeaderValue().toString());
    for (int j = 0; j < n; j++) {
      TableColumn col = new TableColumn(myTable.getColumnModel().getColumn(myTable.getColumnCount() - j - 1).getModelIndex());
      col.setHeaderValue(index - 1 - j);
      col.setIdentifier(index - 1 - j);
      myTable.addColumn(col);
    }
    for (int j = n; j > 0; j--) {
      myTable.moveColumn(myTable.getColumnCount() - j, 0);
    }
  }


  private void removeFirstNRows(int n) {
    if (myTable.getRowCount() == mySize + 5) {
      for (int j = 0; j < n; j++) {
        ((DefaultTableModel)myTable.getModel()).removeRow(0);
      }
    }
  }

  private void removeLastNRows(int n) {
    if (myTable.getRowCount() == mySize + 5) {
      for (int j = 0; j < n; j++) {
        ((DefaultTableModel)myTable.getModel()).removeRow(myTable.getRowCount() - 1);
      }
    }
  }

  private void addLastNRows(int n) {
    for (int j = 0; j < n; j++) {
      ((DefaultTableModel)myTable.getModel()).addRow(new Object[myTable.getColumnCount()]);
    }
  }

  private void addFirstNRows(int n) {
    for (int j = 0; j < n; j++) {
      ((DefaultTableModel)myTable.getModel()).insertRow(0, new Object[myTable.getColumnCount()]);
    }
  }
}
