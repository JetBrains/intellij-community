package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.Nullable;

public class OutCellOutput extends CellOutput{
  private final Integer myPromptNumber;
  public OutCellOutput(@Nullable final String[] text, Integer promptNumber) {
    super(text);
    myPromptNumber = promptNumber;
  }

  public Integer getPromptNumber() {
    return myPromptNumber;
  }
}
