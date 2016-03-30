package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class IpnbOutOutputCell extends IpnbOutputCell {
  public IpnbOutOutputCell(@Nullable final List<String> text, @Nullable Integer promptNumber,
                           @Nullable Map<String, Object> metadata) {
    super(text, promptNumber, metadata);
  }
}
