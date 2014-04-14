package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;

public class StreamCellOutput extends CellOutput {
  @NotNull private final String myLatex;

  public StreamCellOutput(@NotNull final String latex, String[] text) {
    super(text);
    myLatex = latex;
  }

  @NotNull
  public String getLatex() {
    return myLatex;
  }
}
