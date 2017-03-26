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
package com.jetbrains.python.debugger.containerview;

import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.array.ArrayViewStrategy;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.dataframe.DataFrameViewStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;


public abstract class DataViewStrategy {
  private static class StrategyHolder {
    private static final Set<DataViewStrategy> STRATEGIES = ContainerUtil.newHashSet(new ArrayViewStrategy(), new DataFrameViewStrategy());
  }

  public abstract AsyncArrayTableModel createTableModel(int rowCount, int columnCount, @NotNull PyDataViewerPanel panel, @NotNull PyDebugValue debugValue);

  public abstract ColoredCellRenderer createCellRenderer(double minValue, double maxValue, @NotNull ArrayChunk arrayChunk);

  public abstract boolean isNumeric(String dtypeKind);

  @NotNull
  public abstract String getTypeName();

  /**
   * @return null if no strategy for this type
   */
  @Nullable
  public static DataViewStrategy getStrategy(String type) {
    for (DataViewStrategy strategy : StrategyHolder.STRATEGIES) {
      if (strategy.getTypeName().equals(type)) {
        return strategy;
      }
    }
    return null;
  }
}