package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IpnbPngOutputCell extends IpnbImageOutputCell {

  public IpnbPngOutputCell(@NotNull final String png, @Nullable final String[] text, @Nullable final Integer prompt) {
    super(png, text, prompt);
  }

}
