package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class IpnbHtmlOutputCell extends IpnbOutputCell {
  @NotNull private final List<String> myHtml;

  public IpnbHtmlOutputCell(@NotNull final List<String> html,
                            List<String> text,
                            @Nullable final Integer prompt,
                            @Nullable Map<String, Object> metadata) {
    super(text, prompt, metadata);
    myHtml = html;
  }

  @NotNull
  public List<String> getHtmls() {
    return myHtml;
  }
}
