package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class IpnbStreamOutputCell extends IpnbOutputCell {
  @NotNull private final String myStream;

  public IpnbStreamOutputCell(@NotNull final String stream, List<String> text, @Nullable final Integer prompt,
                              @Nullable Map<String, Object> metadata) {
    super(text, prompt, metadata);
    myStream = stream;
  }

  @NotNull
  public String getStream() {
    return myStream;
  }
}
