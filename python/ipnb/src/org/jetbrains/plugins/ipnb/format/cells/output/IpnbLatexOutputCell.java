package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IpnbLatexOutputCell extends IpnbOutputCell {
  @NotNull private final List<String> myLatex;

  public IpnbLatexOutputCell(@NotNull final List<String> latex, Integer promptNumber, @NotNull final List<String> text) {
    super(text, promptNumber);
    myLatex = latex;
  }

  @NotNull
  public List<String> getLatex() {
    return myLatex;
  }
}
