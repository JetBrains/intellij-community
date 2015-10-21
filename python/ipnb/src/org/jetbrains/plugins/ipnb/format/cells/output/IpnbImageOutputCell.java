package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IpnbImageOutputCell extends IpnbOutputCell {
  @NotNull private final String myBase64String;

  public IpnbImageOutputCell(@NotNull final String base64String, @Nullable final List<String> text, @Nullable final Integer prompt) {
    super(text, prompt);
    myBase64String = base64String;
  }

  @NotNull
  public String getBase64String() {
    return myBase64String;
  }
}
