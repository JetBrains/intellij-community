package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IpnbRawCell implements IpnbCell {
  @NotNull private List<String> mySource;

  public IpnbRawCell(@NotNull final List<String> source) {
    mySource = source;
  }

  @NotNull
  public List<String> getSource() {
    return mySource;
  }
}
