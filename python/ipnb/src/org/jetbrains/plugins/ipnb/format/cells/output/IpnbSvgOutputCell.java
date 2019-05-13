package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class IpnbSvgOutputCell extends IpnbOutputCell {
  @NotNull private final List<String> mySvg;

  public IpnbSvgOutputCell(@NotNull final List<String> svg,
                           @Nullable final List<String> text,
                           @Nullable final Integer prompt,
                           @Nullable Map<String, Object> metadata) {
    super(text, prompt, metadata);
    mySvg = svg;
  }

  @NotNull
  public List<String> getSvg() {
    return mySvg;
  }
}
