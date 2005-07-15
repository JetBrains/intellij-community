package com.intellij.formatting;

class SpacingImpl extends Spacing {
  private final int myMinSpaces;
  private int myKeepBlankLines;
  private final int myMaxSpaces;
  private final int myMinLineFeeds;
  private final boolean myIsReadOnly;
  private final boolean myIsSafe;
  private boolean myShouldKeepLineBreaks;
  private boolean myKeepFirstColumn;

  public SpacingImpl(final int minSpaces,
                     final int maxSpaces,
                     final int minLineFeeds,
                     boolean isReadOnly,
                     final boolean safe,
                     final boolean shouldKeepLineBreaks,
                     final int keepBlankLines) {
    myMinSpaces = minSpaces;
    myKeepBlankLines = keepBlankLines;
    myMaxSpaces = Math.max(minSpaces, maxSpaces);
    myMinLineFeeds = minLineFeeds;
    if (minLineFeeds > 0 && minLineFeeds > keepBlankLines) {
      myKeepBlankLines = minLineFeeds;
    }
    myIsReadOnly = isReadOnly;
    myIsSafe = safe;
    myShouldKeepLineBreaks = shouldKeepLineBreaks;
  }


  int getMinSpaces() {
    return myMinSpaces;
  }

  int getMaxSpaces() {
    return myMaxSpaces;
  }

  int getMinLineFeeds() {
    return myMinLineFeeds;
  }

  boolean isReadOnly(){
    return myIsReadOnly;
  }

  boolean containsLineFeeds() {
    return myMinLineFeeds > 0;
  }

  public boolean isSafe() {
    return myIsSafe;
  }

  public void refresh(FormatProcessor formatter) {
  }

  public boolean shouldKeepLineFeeds() {
    return myShouldKeepLineBreaks;
  }

  public int getKeepBlankLines() {
    return myKeepBlankLines;
  }

  public boolean shouldKeepFirstColumn() {
    return myKeepFirstColumn;
  }

  public void setKeepFirstColumn(final boolean keepFirstColumn) {
    myKeepFirstColumn = keepFirstColumn;
  }

  public void setKeepLineBreaks(final int i) {
    myKeepBlankLines=i;
  }

  public void setKeepLineBreaks(final boolean value) {
    myShouldKeepLineBreaks = value;
  }
}
