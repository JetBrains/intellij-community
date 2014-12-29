package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;

public class IpnbLatexOutputCell extends IpnbOutputCell {
  @NotNull private final String[] myLatex;

  public IpnbLatexOutputCell(@NotNull final String[] latex, Integer promptNumber, @NotNull final String[] text) {
    super(text, promptNumber);
    myLatex = latex;
  }

  @NotNull
  public String[] getLatex() {
    return myLatex;
  }
}
