package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;

public abstract class SourceCell implements IpnbCell{
  @NotNull private final String[] mySource;

  SourceCell(@NotNull final String[] source) {
    mySource = source;
  }

  @NotNull
  public String[] getSource() {
    return mySource;
  }
}
