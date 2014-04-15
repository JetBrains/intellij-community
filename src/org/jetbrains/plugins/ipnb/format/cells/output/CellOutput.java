package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.Nullable;

public class CellOutput {
  @Nullable private final String[] myText;

  public CellOutput(@Nullable final String[] text) {
    myText = text;
  }

  @Nullable
  public String[] getText() {
    return myText;
  }
}
