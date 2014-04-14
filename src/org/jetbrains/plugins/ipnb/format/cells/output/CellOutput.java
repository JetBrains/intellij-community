package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;

public class CellOutput {
  @NotNull private final String[] myText;

  public CellOutput(@NotNull final String[] text) {
    myText = text;
  }

  @NotNull
  public String[] getText() {
    return myText;
  }
}
