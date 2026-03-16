// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.dataframe;

import com.jetbrains.python.debugger.containerview.ColumnFilter;
import org.jetbrains.annotations.NotNull;

import javax.swing.RowSorter;
import javax.swing.SortOrder;

public class SeriesViewStrategy extends DataFrameViewStrategy {

  protected SeriesViewStrategy(final @NotNull String typeName) {
    super(typeName);
  }

  public static @NotNull SeriesViewStrategy createInstanceForSeries() {
    return new SeriesViewStrategy("Series");
  }

  public static @NotNull SeriesViewStrategy createInstanceForGeoSeries() {
    return new SeriesViewStrategy("GeoSeries");
  }

  @Override
  public @NotNull String sortModifier(@NotNull String varName, @NotNull RowSorter.SortKey key) {
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
