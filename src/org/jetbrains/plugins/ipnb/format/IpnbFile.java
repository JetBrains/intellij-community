package org.jetbrains.plugins.ipnb.format;

import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;

import java.util.List;

public class IpnbFile {
  private final List<IpnbCell> myCells;

  IpnbFile(List<IpnbCell> cells) {
    myCells = cells;
  }

  public List<IpnbCell> getCells() {
    return myCells;
  }
}
