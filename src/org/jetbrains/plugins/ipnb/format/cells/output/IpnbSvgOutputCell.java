package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IpnbSvgOutputCell extends IpnbOutputCell {
  @NotNull private final String[] mySvg;

  public IpnbSvgOutputCell(@NotNull final String[] svg, @Nullable final String[] text, @Nullable final Integer prompt) {
    super(text, prompt);
    mySvg = svg;
  }

  @NotNull
  public String[] getSvg() {
    return mySvg;
  }
}
