package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PngCellOutput extends CellOutput {
  @NotNull private final String myPng;

  public PngCellOutput(@NotNull final String png, @Nullable final String[] text) {
    super(text);
    myPng = png;
  }

  @NotNull
  public String getPng() {
    return myPng;
  }
}
