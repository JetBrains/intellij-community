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

import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

/**
 * @author amarch
 */
public abstract class FixSizeTableAdjustmentListener<T extends ArrayChunk> implements AdjustmentListener {
  private JBTable myTable;
  private int myRowLimit;
  private int myColLimit;
  private int myViewRows;
  private int myViewCols;
  private int myRowChunkSize;
  private int myColChunkSize;
  private T myEvaluatingChunk = null;
  private int myColOffset;
  private int myRowOffset;

  /**
   * Scrollbar listener for dynamical scrolling with fixed table view size
   *
   * @param table        table with row headers
   * @param rowLimit     maximum row
   * @param columnLimit  maximum column
   * @param viewRows     rows number in view
   * @param viewCols     columns number in view
   * @param rowChunkSize default chunk rows number
   * @param colChunkSize default chunk columns number
   */
  public FixSizeTableAdjustmentListener(JBTable table,
                                        int rowLimit,
                                        int columnLimit,
                                        int viewRows,
                                        int viewCols,
                                        int rowChunkSize,
                                        int colChunkSize) {
    myTable = table;
    myRowLimit = rowLimit;
    myColLimit = columnLimit;
    myViewRows = viewRows;
    myViewCols = viewCols;
    myRowChunkSize = rowChunkSize;
    myColChunkSize = colChunkSize;
    myRowOffset = 0;
    myColOffset = 0;
  }

  @Override
  public void adjustmentValueChanged(AdjustmentEvent e) {
    Adjustable adjustable = e.getAdjustable();

    //reach right or bottom limit
    if (e.getValue() + adjustable.getVisibleAmount() == adjustable.getMaximum()) {
      if (myEvaluatingChunk != null) {
        return;
      }

      if (adjustable.getOrientation() == Adjustable.HORIZONTAL && myTable.getColumnCount() == myViewCols) {
        int rightColumn = myColOffset + myViewCols;
        if (rightColumn >= myColLimit) {
          return;
        }
        int columnSize = Math.min(myColChunkSize, Math.abs(myColLimit - rightColumn));

        final T chunk = createChunk(getBaseSlice(), myViewRows, columnSize, myRowOffset, (myColOffset + myViewCols));
        myEvaluatingChunk = chunk;
        myColOffset += columnSize;

        chunk.fillData(new Runnable() {
          @Override
          public void run() {
            moveNFirstColumns(chunk.getColumns());
            loadDataIntoTable(chunk, myViewCols - chunk.getColumns(), 0);
          }
        });
      }

      else if (adjustable.getOrientation() == Adjustable.VERTICAL && myTable.getRowCount() == myViewRows) {
        int bottomRow = myRowOffset + myViewRows;
        if (bottomRow >= myRowLimit) {
          return;
        }
        int rowSize = Math.min(myRowChunkSize, Math.abs(myRowLimit - bottomRow));

        final T chunk = createChunk(getBaseSlice(), rowSize, myViewCols, bottomRow, myColOffset);
        myEvaluatingChunk = chunk;
        myRowOffset += rowSize;

        chunk.fillData(new Runnable() {
          @Override
          public void run() {
            moveNFirstRows(chunk.getRows());
            loadDataIntoTable(chunk, 0, myViewRows - chunk.getRows());
          }
        });
      }
    }

    //reach left or upper limit
    if (e.getValue() == 0) {
      if (myEvaluatingChunk != null) {
        return;
      }

      if (adjustable.getOrientation() == Adjustable.HORIZONTAL && myTable.getColumnCount() == myViewCols) {
        if (myColOffset != 0) {
          int leftSpace = Math.min(myColChunkSize, myColOffset);

          final T chunk = createChunk(getBaseSlice(), myViewRows, leftSpace, myRowOffset, myColOffset - leftSpace);
          myEvaluatingChunk = chunk;
          myColOffset -= leftSpace;

          chunk.fillData(new Runnable() {
            @Override
            public void run() {
              moveNLastColumns(chunk.getColumns());
              loadDataIntoTable(chunk, 0, 0);
            }
          });
        }
      }

      else if (adjustable.getOrientation() == Adjustable.VERTICAL && myTable.getRowCount() == myViewRows) {
        if (myRowOffset != 0) {
          int upperSpace = Math.min(myRowChunkSize, myRowOffset);

          final T chunk = createChunk(getBaseSlice(), upperSpace, myViewCols, myRowOffset - upperSpace, myColOffset);
          myEvaluatingChunk = chunk;
          myRowOffset -= upperSpace;

          chunk.fillData(new Runnable() {
            @Override
            public void run() {
              moveNLastRows(chunk.getRows());
              loadDataIntoTable(chunk, 0, 0);
            }
          });
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


  private void loadDataIntoTable(T chunk, int hShift, int vShift) {
    Object[][] data = chunk.getData();
    for (int i = 0; i < chunk.getColumns(); i++) {
      for (int j = 0; j < chunk.getRows(); j++) {
        myTable.setValueAt(data[j][i], j + vShift, i + hShift);
      }
    }
    ((ArrayTableForm.JBTableWithRows)myTable).getSliceField().setText(getViewPresentation());
    myEvaluatingChunk = null;
  }

  public String getViewPresentation() {
    return getBaseSlice() +
           "[" +
           myRowOffset +
           ":" +
           (myRowOffset + myViewRows) +
           ", " +
           myColOffset +
           ":" +
           (myColOffset + myViewCols) +
           "]";
  }

  @NotNull
  abstract T createChunk(String baseSlice, int rows, int columns, int rOffset, int cOffset);

  abstract String getBaseSlice();
}
