package org.jetbrains.plugins.ipnb.format;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;

import java.util.List;

public class IpnbFile {
  private final IpnbParser.IpnbFileRaw myRawFile;
  private final List<IpnbCell> myCells;
  private final String myPath;

  IpnbFile(IpnbParser.IpnbFileRaw rawFile, List<IpnbCell> cells, String path) {
    myRawFile = rawFile;
    myCells = cells;
    myPath = path;
  }

  public List<IpnbCell> getCells() {
    return myCells;
  }

  public void addCell(@NotNull final IpnbCell cell, int index) {
    myCells.add(index, cell);
  }

  public void removeCell(int index) {
    myCells.remove(index);
  }

  public String getPath() {
    return myPath;
  }

  public IpnbParser.IpnbFileRaw getRawFile() {
    return myRawFile;
  }
}
