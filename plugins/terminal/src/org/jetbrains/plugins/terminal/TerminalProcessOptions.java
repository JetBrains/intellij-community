// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import org.jetbrains.annotations.Nullable;

public class TerminalProcessOptions {

  private final String myWorkingDirectory;
  private final Integer myInitialColumns; 
  private final Integer myInitialRows;

  public TerminalProcessOptions(@Nullable String workingDirectory, @Nullable Integer initialColumns, @Nullable Integer initialRows) {
    myWorkingDirectory = workingDirectory;
    myInitialColumns = initialColumns;
    myInitialRows = initialRows;
  }

  public @Nullable String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public @Nullable Integer getInitialColumns() {
    return myInitialColumns;
  }

  public @Nullable Integer getInitialRows() {
    return myInitialRows;
  }
}
