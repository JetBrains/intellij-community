package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IpnbJpegOutputCell extends IpnbImageOutputCell {

  public IpnbJpegOutputCell(@NotNull final String jpeg, @Nullable final List<String> text, @Nullable final Integer prompt) {
    super(jpeg, text, prompt);
  }

}
