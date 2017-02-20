/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.debugger.dataframe;

import com.google.common.collect.Maps;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.containerview.PyDataViewerPanel;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.util.List;
import java.util.Map;

/**
 * Created by Yuli Fiterman on 4/26/2016.
 */
public class DataFrameTableModel extends AsyncArrayTableModel {
  private final Map<Integer, ArrayChunk.ColHeader> myColHeaders = Maps.newConcurrentMap();
  private final RowHeaderModel myRowHeaderModel;

  public DataFrameTableModel(int rows,
                             int columns,
                             PyDataViewerPanel dataProvider,
                             PyDebugValue debugValue,
                             DataFrameViewStrategy strategy) {
    super(rows, columns, dataProvider, debugValue, strategy);
    myRowHeaderModel = new RowHeaderModel();
  }
  /* we use labels for the first column so we need to offset columns by one everywhere */

  @Override
  public Object getValueAt(int row, int col) {

    Object value = super.getValueAt(row, col);
    if (value == AsyncArrayTableModel.EMPTY_CELL_VALUE) {
      return value;
    }
    TableValueDescriptor descriptor = createValueWithDescriptor(col, value);
    return descriptor != null ? descriptor : AsyncArrayTableModel.EMPTY_CELL_VALUE;
  }

  private TableValueDescriptor createValueWithDescriptor(int frameCol, Object value) {
    ArrayChunk.ColHeader header = myColHeaders.get(frameCol);
    if (header == null) {
      return null;
    }

    return new TableValueDescriptor(value.toString(), header);
  }


  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return TableValueDescriptor.class;
  }

  @Override
  public String getColumnName(int col) {


    ArrayChunk.ColHeader header = myColHeaders.get(col);
    if (header != null && header.getLabel() != null) {
      return header.getLabel();
    }
    else {
      return super.getColumnName(col);
    }
  }


  @Override
  protected void handleChunkAdded(Integer rowOffset, Integer colOffset, ArrayChunk chunk) {

    myRowHeaderModel.handleChunkAdded(rowOffset, chunk);
    boolean hasNewCols = false;
    List<ArrayChunk.ColHeader> chunkColHeaders = chunk.getColHeaders();
    if (chunkColHeaders != null) {
      for (int i = 0; i < chunkColHeaders.size(); i++) {
        ArrayChunk.ColHeader header = chunkColHeaders.get(i);
        hasNewCols |= (myColHeaders.put(i + colOffset, header) == null);
      }
    }
    if (hasNewCols) {
      UIUtil.invokeLaterIfNeeded(super::fireTableStructureChanged);
    }
  }

  @Override
  public TableModel getRowHeaderModel() {
    return myRowHeaderModel;
  }

  private class RowHeaderModel extends AbstractTableModel {

    private final Map<Integer, String> myRowLabels = Maps.newConcurrentMap();

    @Override
    public int getRowCount() {
      return DataFrameTableModel.this.getRowCount();
    }

    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public String getColumnName(int column) {
      if (column == 0) {
        return "   ";
      }
      throw new IllegalArgumentException("Table only has one column");
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      String s = myRowLabels.get(rowIndex);
      return s == null ? String.valueOf(rowIndex) : s;
    }

    public void handleChunkAdded(Integer rowOffset, ArrayChunk chunk) {
      List<String> chunkRowLabels = chunk.getRowLabels();
      if (chunkRowLabels != null) {
        for (int i = 0; i < chunkRowLabels.size(); i++) {
          String label = chunkRowLabels.get(i);
          String oldValue = myRowLabels.put(i + rowOffset, label);
          if (oldValue == null) {
            final int updatedRow = i + rowOffset;
            UIUtil.invokeLaterIfNeeded(() ->
                                         super.fireTableCellUpdated(updatedRow, 0));
          }
        }
      }
    }
  }
}
