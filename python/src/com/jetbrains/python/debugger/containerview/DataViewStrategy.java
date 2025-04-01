// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.array.ArrayViewStrategy;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.dataframe.DataFrameViewStrategy;
import com.jetbrains.python.debugger.dataframe.SeriesViewStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;


public abstract class DataViewStrategy {
  private static class StrategyHolder {
    private static final Set<DataViewStrategy> STRATEGIES = ImmutableSet.of(
      ArrayViewStrategy.createInstanceForNumpyArray(),
      ArrayViewStrategy.createInstanceForNumpyRecarray(),
      ArrayViewStrategy.createInstanceForEagerTensor(),
      ArrayViewStrategy.createInstanceForResourceVariable(),
      ArrayViewStrategy.createInstanceForSparseTensor(),
      ArrayViewStrategy.createInstanceForTensor(),
      DataFrameViewStrategy.createInstanceForDataFrame(),
      DataFrameViewStrategy.createInstanceForGeoDataFrame(),
      DataFrameViewStrategy.createInstanceForDataset(),
      SeriesViewStrategy.createInstanceForSeries(),
      SeriesViewStrategy.createInstanceForGeoSeries()
    );
  }

  public abstract AsyncArrayTableModel createTableModel(int rowCount,
                                                        int columnCount,
                                                        @NotNull PyDataViewerCommunityPanel panel,
                                                        @NotNull PyDebugValue debugValue);

  public abstract ColoredCellRenderer createCellRenderer(double minValue, double maxValue, @NotNull ArrayChunk arrayChunk);

  public abstract boolean isNumeric(String dtypeKind);

  public abstract @NotNull String sortModifier(@NotNull String varName, @NotNull RowSorter.SortKey key);

  public abstract @NotNull String filterModifier(@NotNull String varName, @NotNull ColumnFilter filter);

  public @Nullable String getInitExecuteString() { return null; }

  public abstract @NotNull String getTypeName();

  public boolean showColumnHeader() {
    return true;
  }

  /**
   * @return null if no strategy for this type
   */
  public static @Nullable DataViewStrategy getStrategy(String type) {
    for (DataViewStrategy strategy : StrategyHolder.STRATEGIES) {
      if (strategy.getTypeName().equals(type)) {
        return strategy;
      }
    }
    return null;
  }
}