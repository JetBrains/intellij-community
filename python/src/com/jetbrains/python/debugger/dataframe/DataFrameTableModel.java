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
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.array.TableChunkDatasource;

import java.util.List;
import java.util.Map;

/**
 * Created by Yuli Fiterma on 4/26/2016.
 */
public class DataFrameTableModel extends AsyncArrayTableModel {
  private final Map<Integer, String> rowLabels = Maps.newConcurrentMap();
  private final Map<Integer, ArrayChunk.ColHeader> colHeaders = Maps.newConcurrentMap();
  public DataFrameTableModel(int rows, int columns, TableChunkDatasource provider) {
    super(rows, columns, provider);
  }
  /* we use labels for the first column so we need to offset columns by one everywhere */

  @Override
  public int getColumnCount() {
    return super.getColumnCount() +1;
  }

  @Override
  public Object getValueAt(int row, int col) {
    if (col == 0) {
      return getRowHeader(row);
    }
    else
    {
      int frameCol = col - 1;
      Object value = super.getValueAt(row, frameCol);
      if ( value == AsyncArrayTableModel.EMPTY_CELL_VALUE)
      {
         return value;
      }
      TableValueDescriptor descriptor = createValueWithDescriptor(frameCol, value);
      return descriptor != null ? descriptor : AsyncArrayTableModel.EMPTY_CELL_VALUE;
    }
  }

  private TableValueDescriptor createValueWithDescriptor(int frameCol, Object value) {
    ArrayChunk.ColHeader header = colHeaders.get(frameCol);
    if (header == null)
    {
       return null;
    }

    return new TableValueDescriptor(value.toString(), header);

  }

  private String getRowHeader(int row)
  {
    String s = rowLabels.get(row);
    return s == null ? String.valueOf(row) : s;

  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
    return columnIndex == 0 ? String.class : TableValueDescriptor.class;
  }

  @Override
  public String getColumnName(int col) {
    if (col == 0)
    {
       return "   ";
    }
    else
    {

      int frameColumn = col - 1;
      ArrayChunk.ColHeader header = colHeaders.get(frameColumn);
      if (header != null && header.getLabel() != null)
      {
         return header.getLabel();
      }
      else
      {
        return super.getColumnName(frameColumn);

      }
    }
  }


  @Override
  protected void handleChunkAdded(Integer rowOffset, Integer colOffset, ArrayChunk chunk) {
    List<String> chunkRowLabels = chunk.getRowLabels();
    if (chunkRowLabels != null)
    {
      for (int i = 0; i < chunkRowLabels.size(); i++) {
        String label = chunkRowLabels.get(i);
        String oldValue = rowLabels.put(i + rowOffset, label);
        if (oldValue == null)
        {
          final int updatedRow = i + rowOffset;
          UIUtil.invokeLaterIfNeeded(()->
                                       super.fireTableCellUpdated(updatedRow, 0));
        }
      }
    }
    boolean hasNewCols = false;
    List<ArrayChunk.ColHeader> chunkColHeaders = chunk.getColHeaders();
    if (chunkColHeaders != null)
    {
      for (int i = 0; i < chunkColHeaders.size(); i++) {
        ArrayChunk.ColHeader header = chunkColHeaders.get(i);
        hasNewCols |= (colHeaders.put(i + colOffset, header) == null);

      }
    }
    if (hasNewCols)
    {
       UIUtil.invokeLaterIfNeeded(super::fireTableStructureChanged);
    }
  }

  @Override
  public void fireTableCellUpdated(int row, int column) {
    super.fireTableCellUpdated(row, column + 1);
  }
}
