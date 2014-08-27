package org.jetbrains.plugins.ipnb.format.cells.output;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IpnbErrorOutputCell extends IpnbOutputCell {

  @NotNull private final String myEvalue;
  @NotNull private final String myEname;

  public IpnbErrorOutputCell(@NotNull final String evalue, @NotNull final String ename, @NotNull final String[] traceback,
                             @Nullable final Integer prompt) {
    super(traceback, prompt);
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
