package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;

public class IpnbHeadingCell extends IpnbEditableCell {
  private int myLevel;

  public IpnbHeadingCell(@NotNull final String[] source, int level) {
    super(source);
    myLevel = level;
  }

  public int getLevel() {
    return myLevel;
  }

  public void setLevel(int level) {
    myLevel = level;
  }

  @SuppressWarnings({"CloneDoesntCallSuperClone", "CloneDoesntDeclareCloneNotSupportedException"})
  @Override
  public Object clone() {
    return new IpnbHeadingCell(getSource(), myLevel);
  }
}
