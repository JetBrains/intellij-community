package org.jetbrains.plugins.ipnb.format.cells;

import org.jetbrains.annotations.NotNull;

public class HeadingCell extends SourceCell {
  private final int myLevel;

  public HeadingCell(@NotNull final String[] source, int level) {
    super(source);
    myLevel = level;
  }

  public int getLevel() {
    return myLevel;
  }
}
