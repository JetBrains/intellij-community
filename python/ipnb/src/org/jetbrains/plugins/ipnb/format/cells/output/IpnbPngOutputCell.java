package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class IpnbPngOutputCell extends IpnbImageOutputCell {

  public IpnbPngOutputCell(@Nullable final String png, @Nullable final List<String> text, @Nullable final Integer prompt,
                           @Nullable Map<String, Object> metadata) {
    super(png, text, prompt, metadata);
  }

}
