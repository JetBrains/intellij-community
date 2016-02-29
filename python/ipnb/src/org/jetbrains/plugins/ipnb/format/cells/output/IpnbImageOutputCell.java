package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class IpnbImageOutputCell extends IpnbOutputCell {
  @Nullable private final String myBase64String;

  public IpnbImageOutputCell(@Nullable final String base64String,
                             @Nullable final List<String> text,
                             @Nullable final Integer prompt,
                             @Nullable Map<String, Object> metadata) {
    super(text, prompt, metadata);
    myBase64String = base64String;
  }

  @Nullable
  public String getBase64String() {
    return myBase64String;
  }
}
