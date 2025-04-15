// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.dataframe;

import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.containerview.ColoredCellRenderer;
import com.jetbrains.python.debugger.containerview.ColumnFilter;
import com.jetbrains.python.debugger.containerview.DataViewStrategy;
import com.jetbrains.python.debugger.containerview.PyDataViewerCommunityPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DataFrameViewStrategy extends DataViewStrategy {

  private final String myTypeName;

  public static @NotNull DataFrameViewStrategy createInstanceForDataFrame() {
    return new DataFrameViewStrategy("DataFrame");
  }

  public static @NotNull DataFrameViewStrategy createInstanceForGeoDataFrame() {
    return new DataFrameViewStrategy("GeoDataFrame");
  }

  public static @NotNull DataFrameViewStrategy createInstanceForDataset() {
    return new DataFrameViewStrategy("Dataset");
  }

  protected DataFrameViewStrategy(final @NotNull String typeName) {
    this.myTypeName = typeName;
  }

  @Override
  public AsyncArrayTableModel createTableModel(int rowCount,
                                               int columnCount,
                                               @NotNull PyDataViewerCommunityPanel dataProvider,
                                               @NotNull PyDebugValue debugValue) {
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

  @Override
  public @NotNull String sortModifier(@NotNull String varName, @NotNull RowSorter.SortKey key) {
    return String.format("%s.sort_values(by=%s.columns[%d]%s)", varName, varName, key.getColumn(),
                         key.getSortOrder() == SortOrder.ASCENDING ? "" : ", ascending=False");
  }

  @Override
  public @NotNull String filterModifier(@NotNull String varName, @NotNull ColumnFilter filter) {
    if (filter.isSubstring()) {
      return String.format("%1$s[%1$s.iloc[:, %2$d].apply(str).str.contains('%3$s', regex=%4$s)]",
                           varName, filter.getColumn(), filter.getFilter(), filter.isRegex() ? "True" : "False");
    }

    return String.format("%1$s[%1$s.iloc[:, %2$d].apply(lambda %4$s: bool(%3$s))]",
                         varName, filter.getColumn(), filter.getFilter(), ColumnFilter.VAR_ALIAS);
  }

  @Override
  public @NotNull String getTypeName() {
    return myTypeName;
  }
}
