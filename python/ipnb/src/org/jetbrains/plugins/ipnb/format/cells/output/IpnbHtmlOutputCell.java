package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class IpnbHtmlOutputCell extends IpnbOutputCell {
  @NotNull private final List<String> myHtml;

  public IpnbHtmlOutputCell(@NotNull final List<String> html, List<String> text, @Nullable final Integer prompt) {
    super(text, prompt);
    myHtml = html;
  }

  @NotNull
  public List<String> getHtmls() {
    return myHtml;
  }
}
