package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SvgCellOutput extends CellOutput {

  @NotNull private final String[] mySvg;

  public SvgCellOutput(@NotNull final String[] svg, @Nullable final String[] text) {
    super(text);
    mySvg = svg;
  }

  @NotNull
  public String[] getSvg() {
    return mySvg;
  }
}
