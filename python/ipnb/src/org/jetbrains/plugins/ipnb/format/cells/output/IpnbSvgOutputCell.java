package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IpnbSvgOutputCell extends IpnbOutputCell {
  @NotNull private final List<String> mySvg;

  public IpnbSvgOutputCell(@NotNull final List<String> svg, @Nullable final List<String> text, @Nullable final Integer prompt) {
    super(text, prompt);
    mySvg = svg;
  }

  @NotNull
  public List<String> getSvg() {
    return mySvg;
  }
}
