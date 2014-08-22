package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;

public class ErrorCellOutput extends CellOutput {

  @NotNull private final String myEvalue;
  @NotNull private final String myEname;

  public ErrorCellOutput(@NotNull final String evalue, @NotNull final String ename, @NotNull final String[] traceback) {
    super(traceback);
    myEvalue = evalue;
    myEname = ename;
  }

  @NotNull
  public String getEvalue() {
    return myEvalue;
  }

  @NotNull
  public String getEname() {
    return myEname;
  }
}
