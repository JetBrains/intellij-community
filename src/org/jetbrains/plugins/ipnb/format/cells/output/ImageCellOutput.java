package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImageCellOutput extends CellOutput {
  @NotNull private final String myBase64String;

  public ImageCellOutput(@NotNull final String base64String, @Nullable final String[] text) {
    super(text);
    myBase64String = base64String;
  }

  @NotNull
  public String getBase64String() {
    return myBase64String;
  }
}
