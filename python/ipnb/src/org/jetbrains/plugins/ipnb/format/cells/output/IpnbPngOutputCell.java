package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IpnbPngOutputCell extends IpnbImageOutputCell {

  public IpnbPngOutputCell(@NotNull final String png, @Nullable final List<String> text, @Nullable final Integer prompt) {
    super(png, text, prompt);
  }

}
