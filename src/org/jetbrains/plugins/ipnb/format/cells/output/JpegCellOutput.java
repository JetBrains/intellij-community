package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JpegCellOutput extends ImageCellOutput {

  public JpegCellOutput(@NotNull final String jpeg, @Nullable final String[] text) {
    super(jpeg, text);
  }

}
