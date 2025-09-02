// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.dataframe;

import com.google.common.collect.Maps;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.containerview.PyDataViewerCommunityPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.util.List;
import java.util.Map;

public class DataFrameTableModel extends AsyncArrayTableModel {
  private final Map<Integer, ArrayChunk.ColHeader> myColHeaders = Maps.newConcurrentMap();
  private final RowHeaderModel myRowHeaderModel;

  public DataFrameTableModel(int rows,
                             int columns,
                             PyDataViewerCommunityPanel dataProvider,
                             PyDebugValue debugValue,
                             DataFrameViewStrategy strategy) {
    super(rows, columns, dataProvider, debugValue, strategy);
    myRowHeaderModel = new RowHeaderModel();
  }
  /* we use labels for the first column, so we need to offset columns by one everywhere */

  @Override
  public Object getValueAt(int row, int col) {

    Object value = super.getValueAt(row, col);
    if (value == EMPTY_CELL_VALUE) {
      return value;
    }
    TableValueDescriptor descriptor = createValueWithDescriptor(col, value);
    return descriptor != null ? descriptor : EMPTY_CELL_VALUE;
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

  public @Nullable String getColumnType(int col) {
    ArrayChunk.ColHeader header = myColHeaders.get(col);
    if (header != null)
      return header.getType();

    return null;
  }

  @Override
  protected void handleChunkAdded(Integer rowOffset, Integer colOffset, ArrayChunk chunk) {
    myRowHeaderModel.handleChunkAdded(rowOffset, chunk);
    boolean structureChanged = false;
    List<ArrayChunk.ColHeader> chunkColHeaders = chunk.getColHeaders();
    if (chunkColHeaders != null) {
      int i = 0;
      for (ArrayChunk.ColHeader header: chunkColHeaders) {
        ArrayChunk.ColHeader old = myColHeaders.put(i + colOffset, header);
        if (old == null || !old.getLabel().equals(header.getLabel())) {
          structureChanged = true;
        }
        i++;
      }
    }
    if (structureChanged) {
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
