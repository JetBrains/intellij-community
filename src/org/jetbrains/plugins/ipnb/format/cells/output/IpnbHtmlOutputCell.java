package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IpnbHtmlOutputCell extends IpnbOutputCell {
  @NotNull private final String[] myHtml;

  public IpnbHtmlOutputCell(@NotNull final String[] html, String[] text, @Nullable final Integer prompt) {
    super(text, prompt);
    myHtml = html;
  }

  @NotNull
  public String[] getHtmls() {
    return myHtml;
  }
}
