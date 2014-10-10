package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IpnbStreamOutputCell extends IpnbOutputCell {
  @NotNull private final String myStream;

  public IpnbStreamOutputCell(@NotNull final String stream, String[] text, @Nullable final Integer prompt) {
    super(text, prompt);
    myStream = stream;
  }

  @NotNull
  public String getStream() {
    return myStream;
  }
}
