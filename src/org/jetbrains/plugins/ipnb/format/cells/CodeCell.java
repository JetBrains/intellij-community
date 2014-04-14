package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.output.CellOutput;

import java.util.List;

public class CodeCell implements IpnbCell {
  @NotNull private final String myLanguage;
  @NotNull private final String[] myInput;
  private final int myPromptNumber;
  @NotNull private final List<CellOutput> myCellOutputs;

  public CodeCell(@NotNull final String language, @NotNull final String[] input, int number, @NotNull final List<CellOutput> cellOutputs) {
    myLanguage = language;
    myInput = input;
    myPromptNumber = number;
    myCellOutputs = cellOutputs;
  }

  @NotNull
  public String getLanguage() {
    return myLanguage;
  }

  @NotNull
  public String[] getInput() {
    return myInput;
  }

  public int getPromptNumber() {
    return myPromptNumber;
  }

  @NotNull
  public List<CellOutput> getCellOutputs() {
    return myCellOutputs;
  }
}
