package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PngCellOutput extends ImageCellOutput {

  public PngCellOutput(@NotNull final String png, @Nullable final String[] text) {
    super(png, text);
  }

}
