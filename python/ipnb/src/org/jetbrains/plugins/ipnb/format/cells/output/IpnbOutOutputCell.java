package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IpnbOutOutputCell extends IpnbOutputCell {
  public IpnbOutOutputCell(@Nullable final List<String> text, Integer promptNumber) {
    super(text, promptNumber);
  }
}
