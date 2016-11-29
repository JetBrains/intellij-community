package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class IpnbLatexOutputCell extends IpnbOutputCell {
  @NotNull private final List<String> myLatex;
  private final boolean isMarkdown;

  public IpnbLatexOutputCell(@NotNull final List<String> latex, final boolean markdown, Integer promptNumber,
                             @NotNull final List<String> text, @Nullable Map<String, Object> metadata) {
    super(text, promptNumber, metadata);
    myLatex = latex;
    isMarkdown = markdown;
  }

  @NotNull
  public List<String> getLatex() {
    return myLatex;
  }

  public boolean isMarkdown() {
    return isMarkdown;
  }
}
