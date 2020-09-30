// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.containerview;

import org.jetbrains.annotations.NotNull;

public class ColumnFilter {
  public enum Mode {
    EXPRESSION,
    SUBSTRING,
    REGEX
  }

  public static final String VAR_ALIAS = "x";

  private final int myColumn;
  @NotNull private final String myFilter;

  private final Mode myMode;
  @NotNull private final String myStrFormat;

  public ColumnFilter(int column, @NotNull String filter, Mode mode, @NotNull String strFormat) {
    myColumn = column;
    myFilter = filter;
    myMode = mode;
    myStrFormat = strFormat;
  }

  public ColumnFilter(int column, @NotNull String filter, Mode mode) {
    this(column, filter, mode, "%s");
  }

  public Mode getMode() {
    return myMode;
  }

  public int getColumn() {
    return myColumn;
  }

  public boolean isSubstring() {
    return myMode == Mode.SUBSTRING || myMode == Mode.REGEX;
  }

  public boolean isRegex() {
    return myMode == Mode.REGEX;
  }

  public boolean isExpression() {
    return myMode == Mode.EXPRESSION;
  }

  @NotNull
  public String getFilter() {
    return myFilter;
  }

  @NotNull
  public String getStrFormat() {
    return myStrFormat;
  }

  @NotNull
  public String replaceAlias(@NotNull String replacement) {
    return myFilter.replace(VAR_ALIAS, replacement);
  }

  public boolean isEmpty() {
    return myFilter.isEmpty();
  }
}
