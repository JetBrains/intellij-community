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
package com.jetbrains.python.debugger.array;

import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.debugger.containerview.ColumnFilter;
import com.jetbrains.python.debugger.containerview.DataViewStrategy;
import com.jetbrains.python.debugger.containerview.PyDataViewerPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ArrayViewStrategy extends DataViewStrategy {
  private static final String NDARRAY = "ndarray";

  @Override
  public AsyncArrayTableModel createTableModel(int rowCount,
                                               int columnCount,
                                               @NotNull PyDataViewerPanel panel,
                                               @NotNull PyDebugValue debugValue) {
    return new AsyncArrayTableModel(rowCount, columnCount, panel, debugValue, this);
  }

  @Override
  public ColoredCellRenderer createCellRenderer(double minValue, double maxValue, @NotNull ArrayChunk arrayChunk) {
    ArrayTableCellRenderer renderer = new ArrayTableCellRenderer(minValue, maxValue, arrayChunk.getType());
    renderer.fillColorRange(arrayChunk.getMin(), arrayChunk.getMax());
    return renderer;
  }

  @Override
  public boolean isNumeric(String dtypeKind) {
    if (dtypeKind != null) {
      return "biufc".contains(dtypeKind.substring(0, 1));
    }
    return false;
  }

  @Override
  public @NotNull String sortModifier(@NotNull String varName, @NotNull RowSorter.SortKey key) {
    return String.format("%s[%s[:,%d].argsort()%s]", varName, varName, key.getColumn(),
                         key.getSortOrder() == SortOrder.ASCENDING ? "" : "[::-1]");
  }

  @Override
  public @NotNull String filterModifier(@NotNull String varName, @NotNull ColumnFilter filter) {
    if (filter.isSubstring()) {
      throw new UnsupportedOperationException("Substring search is not supported on numpy arrays");
    }

    return String.format("%1$s[_np_vectorize(lambda %4$s: bool(%2$s))(%1$s[:, %3$d])]",
                         varName, filter.getFilter(), filter.getColumn(), ColumnFilter.VAR_ALIAS);
  }

  @Override
  public @Nullable String getInitExecuteString() {
    return "import numpy as _np; _np_vectorize = _np.vectorize";
  }

  @NotNull
  @Override
  public String getTypeName() {
    return NDARRAY;
  }
}
