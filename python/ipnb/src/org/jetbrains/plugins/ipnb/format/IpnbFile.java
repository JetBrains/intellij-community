package org.jetbrains.plugins.ipnb.format;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;

import java.util.List;
import java.util.Map;

public class IpnbFile {
  private final Map<String, Object> myMetadata;
  private final int myNbformat;
  private final List<IpnbCell> myCells;
  private final String myPath;

  public IpnbFile(Map<String, Object> metadata, int nbformat, List<IpnbCell> cells, String path) {
    myMetadata = metadata;
    myNbformat = nbformat;
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

  public Map<String, Object> getMetadata () {
    return myMetadata;
  }

  public int getNbformat() {
    return myNbformat;
  }

}