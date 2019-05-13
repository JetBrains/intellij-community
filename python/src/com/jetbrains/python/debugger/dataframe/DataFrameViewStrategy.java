// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.dataframe;

import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.debugger.containerview.DataViewStrategy;
import com.jetbrains.python.debugger.containerview.PyDataViewerPanel;
import org.jetbrains.annotations.NotNull;

public class DataFrameViewStrategy extends DataViewStrategy {
  private static final String DATA_FRAME = "DataFrame";

  @Override
  public AsyncArrayTableModel createTableModel(int rowCount, int columnCount, @NotNull PyDataViewerPanel dataProvider, @NotNull PyDebugValue debugValue) {
    return new DataFrameTableModel(rowCount, columnCount, dataProvider, debugValue, this);
  }

  @Override
  public ColoredCellRenderer createCellRenderer(double minValue, double maxValue, @NotNull ArrayChunk arrayChunk) {
    return new DataFrameTableCellRenderer();
  }

  @Override
  public boolean isNumeric(String dtypeKind) {
    return true;
  }

  @NotNull
  @Override
  public String getTypeName() {
    return DATA_FRAME;
  }
}
