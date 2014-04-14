package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;

public class PngCellOutput extends CellOutput {
  @NotNull private final String myPng;

  public PngCellOutput(@NotNull final String png, @NotNull final String[] text) {
    super(text);
    myPng = png;
  }

  @NotNull
  public String getPng() {
    return myPng;
  }
}
