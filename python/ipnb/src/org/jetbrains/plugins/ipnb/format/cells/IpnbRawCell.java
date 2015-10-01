package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;

public class IpnbRawCell implements IpnbCell {
  @NotNull private String[] mySource;

  public IpnbRawCell(@NotNull final String[] source) {
    mySource = source;
  }

  @NotNull
  public String[] getSource() {
    return mySource;
  }
}
