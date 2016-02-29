package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class IpnbHeadingCell extends IpnbEditableCell {
  private int myLevel;   // from 1 to 6

  public IpnbHeadingCell(@NotNull final List<String> source, int level, @Nullable Map<String, Object> metadata) {
    super(source, metadata);
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
    return new IpnbHeadingCell(getSource(), myLevel, myMetadata);
  }
}
