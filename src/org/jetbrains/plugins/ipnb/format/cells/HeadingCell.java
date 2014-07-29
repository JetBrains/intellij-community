package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;

public class HeadingCell extends SourceCell {
  private int myLevel;

  public HeadingCell(@NotNull final String[] source, int level) {
    super(source);
    myLevel = level;
  }

  public int getLevel() {
    return myLevel;
  }

  public void setLevel(int level) {
    myLevel = level;
  }
}
