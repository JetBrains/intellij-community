package com.intellij.newCodeFormatting;

public class SpaceProperty {
  private final int myMinSpaces;
  private final int myMaxSpaces;
  private final int myMinLineFeeds;
  private final int myMaxLineFeeds;
  private final boolean myIsReadOnly;

  public SpaceProperty(final int minSpaces, final int maxSpaces, final int minLineFeeds, final int maxLineFeeds, boolean isReadOnly) {
    myMinSpaces = minSpaces;
    myMaxSpaces = Math.max(minSpaces, maxSpaces);
    myMinLineFeeds = minLineFeeds;
    myMaxLineFeeds = Math.max(minLineFeeds, maxLineFeeds);
    myIsReadOnly = isReadOnly;
  }


  public int getMinSpaces() {
    return myMinSpaces;
  }

  public int getMaxSpaces() {
    return myMaxSpaces;
  }

  public int getMinLineFeeds() {
    return myMinLineFeeds;
  }

  public int getMaxLineFeeds() {
    return myMaxLineFeeds;
  }

  public boolean isReadOnly(){
    return myIsReadOnly;
  }
}
