package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;

public class LatexCellOutput extends CellOutput {
  @NotNull private final String[] myLatex;
  private final int myPromptNumber;

  public LatexCellOutput(@NotNull final String[] latex, int promptNumber, @NotNull final String[] text) {
    super(text);
    myLatex = latex;
    myPromptNumber = promptNumber;
  }

  @NotNull
  public String[] getLatex() {
    return myLatex;
  }

  public int getPromptNumber() {
    return myPromptNumber;
  }
}
