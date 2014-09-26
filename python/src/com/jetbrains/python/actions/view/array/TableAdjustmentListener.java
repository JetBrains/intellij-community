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
  private int myRSize;
  private int myCSize;
  private int myRChunk;
  private int myCChunk;

  public static final int VERTICAL_MODE = 0;
  public static final int HORIZONTAL_MODE = 1;

  public TableAdjustmentListener(JBTable table, int rMax, int cMax, int mode, int rSize, int cSize, int rChunk, int cChunk) {
    myTable = table;
    this.rMax = rMax;
    this.cMax = cMax;
    myMode = mode;
    myRSize = rSize;
    myCSize = cSize;
    myRChunk = rChunk;
    myCChunk = cChunk;
  }

  @Override
  public void adjustmentValueChanged(AdjustmentEvent e) {

    //reach right or bottom border
    if (e.getValue() + e.getAdjustable().getVisibleAmount() == e.getAdjustable().getMaximum()) {
      if (myMode == HORIZONTAL_MODE && myTable.getColumnCount() == myCSize) {

        int x = Integer.parseInt(myTable.getColumnModel().getColumn(myCSize - 1).getHeaderValue().toString()) + 1;
        if (x >= cMax) {
          return;
        }
        int cS = myCChunk;
        if (x + myCChunk >= cMax) {
          cS = Math.min(myCChunk, Math.abs(cMax-x));
        }
        int y = myTable.getRowCount();
        String repr = "val[" +
                      ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable().getRowShift() +
                      ":, " +
                      (Integer.parseInt(myTable.getColumnModel().getColumn(0).getHeaderValue().toString()) + cS) +
                      ":]";

        ArrayChunk chunk =
          new ArrayChunk(repr, y, cS, x, ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable().getRowShift());
        chunk.loadData();

        moveNFirstColumns(chunk.getColumns());
        loadDataIntoTable(chunk, myCSize - chunk.getColumns(), 0);
      }
      else if (myMode == VERTICAL_MODE && myTable.getRowCount() == myRSize) {


        RowNumberTable rowTable = ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable();
        int x = rowTable.getRowShift() + myRSize;
        if (x == cMax) {
          return;
        }
        int rS = myRChunk;
        if (x + myRChunk >= rMax) {
          rS = Math.min(myRChunk, Math.abs(rMax-x));
        }
        int y = myTable.getColumnCount();
        String repr = "val[" +
                      (((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable().getRowShift() + rS) +
                      ":, " +
                      Integer.parseInt(myTable.getColumnModel().getColumn(0).getHeaderValue().toString()) +
                      ":]";

        ArrayChunk chunk =
          new ArrayChunk(repr, rS, y, Integer.parseInt(myTable.getColumnModel().getColumn(0).getHeaderValue().toString()), x);
        chunk.loadData();

        moveNFirstRows(chunk.getRows());
        loadDataIntoTable(chunk, 0, myRSize - chunk.getRows());
      }
    }

    //reach left or upper border
    if (e.getValue() == 0) {
      if (myMode == HORIZONTAL_MODE && myTable.getColumnCount() == myCSize) {

        int x = Integer.parseInt(myTable.getColumnModel().getColumn(0).getHeaderValue().toString());
        if (x != 0) {
          int y = myTable.getRowCount();
          int leftSpace = Math.min(myCChunk, x);
          String repr = "val[" + ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable().getRowShift() + ":, " + (x-leftSpace) + ":]";

          ArrayChunk chunk =
            new ArrayChunk(repr, y, leftSpace, x - leftSpace, ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable().getRowShift());
          chunk.loadData();

          moveNLastColumns(chunk.getColumns());
          loadDataIntoTable(chunk, 0, 0);
        }
      }
      else if (myMode == VERTICAL_MODE && myTable.getRowCount() == myRSize) {

        RowNumberTable rowTable = ((ArrayTableForm.JBTableWithRows)myTable).getRowNumberTable();
        int x = rowTable.getRowShift();
        if (x != 0) {
          int y = myTable.getColumnCount();
          int leftSpace = Math.min(myRChunk, x);
          String repr = "val[" + (x-leftSpace) + ":, " + Integer.parseInt(myTable.getColumnModel().getColumn(0).getHeaderValue().toString()) + ":]";

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
    ((ArrayTableForm.JBTableWithRows)myTable).getSliceField().setText(chunk.getDataCommand());
  }
}
