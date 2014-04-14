package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;

public abstract class SourceCell implements IpnbCell{
  @NotNull private final String[] mySource;

  SourceCell(@NotNull final String[] source) {
    mySource = source;
  }

  public String[] getSource() {
    return mySource;
  }
}
