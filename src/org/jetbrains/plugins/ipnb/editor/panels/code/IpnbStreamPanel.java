package org.jetbrains.plugins.ipnb.editor.panels.code;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.output.IpnbStreamOutputCell;

public class IpnbStreamPanel extends IpnbCodeOutputPanel<IpnbStreamOutputCell> {
  public IpnbStreamPanel(@NotNull final IpnbStreamOutputCell cell) {
    super(cell);
  }
}
