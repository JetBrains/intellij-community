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

import com.jetbrains.python.debugger.containerview.ColumnFilter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SeriesViewStrategy extends DataFrameViewStrategy {

  protected SeriesViewStrategy(@NotNull final String typeName) {
    super(typeName);
  }

  @NotNull
  public static SeriesViewStrategy createInstanceForSeries() {
    return new SeriesViewStrategy("Series");
  }

  @NotNull
  public static SeriesViewStrategy createInstanceForGeoSeries() {
    return new SeriesViewStrategy("GeoSeries");
  }

  @Override
  @NotNull
  public String sortModifier(@NotNull String varName, @NotNull RowSorter.SortKey key) {
    return String.format("%s.sort_values(%s)", varName,
                         key.getSortOrder() == SortOrder.ASCENDING ? "" : "ascending=False");
  }

  @Override
  public @NotNull String filterModifier(@NotNull String varName, @NotNull ColumnFilter filter) {
    if (filter.isSubstring()) {
      return String.format("%1$s[%1$s.apply(str).str.contains('%2$s', regex=%3$s)]",
                           varName, filter.getFilter(), filter.isRegex() ? "True" : "False");
    }

    return String.format("%1$s[%1$s.apply(lambda %3$s: bool(%2$s))]", varName, filter.getFilter(), ColumnFilter.VAR_ALIAS);
  }
}
