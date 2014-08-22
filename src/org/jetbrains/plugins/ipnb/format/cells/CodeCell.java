package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.format.cells.output.CellOutput;

import java.util.List;

public class CodeCell extends SourceCell {
  @NotNull private final String myLanguage;
  @Nullable private final Integer myPromptNumber;
  @NotNull private final List<CellOutput> myCellOutputs;

  public CodeCell(@NotNull final String language, @NotNull final String[] input, Integer number, @NotNull final List<CellOutput> cellOutputs) {
    super(input);
    myLanguage = language;
    myPromptNumber = number;
    myCellOutputs = cellOutputs;
  }

  @NotNull
  public String getLanguage() {
    return myLanguage;
  }

  @Nullable
  public Integer getPromptNumber() {
    return myPromptNumber;
  }

  @NotNull
  public List<CellOutput> getCellOutputs() {
    return myCellOutputs;
  }

  public void removeCellOutputs() {
    myCellOutputs.clear();
  }

  public void addCellOutput(@NotNull final CellOutput cellOutput) {
    myCellOutputs.add(cellOutput);
  }
}
