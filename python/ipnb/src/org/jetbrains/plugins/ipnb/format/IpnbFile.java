package org.jetbrains.plugins.ipnb.format;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.format.cells.IpnbCell;

import java.util.List;
import java.util.Map;

public class IpnbFile {
  private final Map<String, Object> myMetadata;
  private final int myNbformat;
  private final int myNbFormatMinor;
  private final List<IpnbCell> myCells;
  @NotNull private final String myPath;

  public IpnbFile(Map<String, Object> metadata, int nbformat, int nbFormatMinor, List<IpnbCell> cells, @NotNull String path) {
    myMetadata = metadata;
    myNbformat = nbformat;
    myNbFormatMinor = nbFormatMinor;
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

  @NotNull
  public String getPath() {
    return myPath;
  }

  public Map<String, Object> getMetadata() {
    return myMetadata;
  }

  public int getNbformat() {
    return myNbformat;
  }

  public int getNbFormatMinor() {
    return myNbFormatMinor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IpnbFile file = (IpnbFile)o;

    if (myNbformat != file.myNbformat) return false;
    if (myNbFormatMinor != file.myNbFormatMinor) return false;
    if (myMetadata != null ? !myMetadata.equals(file.myMetadata) : file.myMetadata != null) return false;
    if (myCells != null ? !myCells.equals(file.myCells) : file.myCells != null) return false;
    if (!myPath.equals(file.myPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMetadata != null ? myMetadata.hashCode() : 0;
    result = 31 * result + myNbformat;
    result = 31 * result + myNbFormatMinor;
    result = 31 * result + (myCells != null ? myCells.hashCode() : 0);
    result = 31 * result + myPath.hashCode();
    return result;
  }
}