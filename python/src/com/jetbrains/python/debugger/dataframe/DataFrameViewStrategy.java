/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.debugger.containerview.DataViewStrategy;
import com.jetbrains.python.debugger.containerview.PyDataViewerPanel;
import org.jetbrains.annotations.NotNull;

public class DataFrameViewStrategy extends DataViewStrategy {
  private static final String DATA_FRAME = "DataFrame";

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
