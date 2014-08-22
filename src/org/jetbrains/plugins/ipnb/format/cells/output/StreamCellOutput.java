package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;

public class StreamCellOutput extends CellOutput {
  @NotNull private final String myStream;

  public StreamCellOutput(@NotNull final String stream, String[] text) {
    super(text);
    myStream = stream;
  }

  @NotNull
  public String getStream() {
    return myStream;
  }
}
