// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.array;

import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.debugger.containerview.ColumnFilter;
import com.jetbrains.python.debugger.containerview.DataViewStrategy;
import com.jetbrains.python.debugger.containerview.PyDataViewerCommunityPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ArrayViewStrategy extends DataViewStrategy {
  private final String myTypeName;

  public static @NotNull ArrayViewStrategy createInstanceForNumpyArray() {
    return new ArrayViewStrategy("ndarray");
  }

  public static @NotNull ArrayViewStrategy createInstanceForNumpyRecarray() {
    return new ArrayViewStrategy("recarray");
  }

  public static @NotNull ArrayViewStrategy createInstanceForEagerTensor() {
    return new ArrayViewStrategy("EagerTensor");
  }

  public static @NotNull ArrayViewStrategy createInstanceForResourceVariable() {
    return new ArrayViewStrategy("ResourceVariable");
  }

  public static @NotNull ArrayViewStrategy createInstanceForSparseTensor() {
    return new ArrayViewStrategy("SparseTensor");
  }

  public static @NotNull ArrayViewStrategy createInstanceForTensor() {
    return new ArrayViewStrategy("Tensor");
  }

  protected ArrayViewStrategy(final @NotNull String typeName) {
    this.myTypeName = typeName;
  }

  @Override
  public AsyncArrayTableModel createTableModel(int rowCount,
                                               int columnCount,
                                               @NotNull PyDataViewerCommunityPanel panel,
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

  @Override
  public @NotNull String getTypeName() {
    return myTypeName;
  }
}
