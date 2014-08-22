package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;

public class LatexCellOutput extends CellOutput {
  @NotNull private final String[] myLatex;
  private final Integer myPromptNumber;

  public LatexCellOutput(@NotNull final String[] latex, Integer promptNumber, @NotNull final String[] text) {
    super(text);
    myLatex = latex;
    myPromptNumber = promptNumber;
  }

  @NotNull
  public String[] getLatex() {
    return myLatex;
  }

  public Integer getPromptNumber() {
    return myPromptNumber;
  }
}
