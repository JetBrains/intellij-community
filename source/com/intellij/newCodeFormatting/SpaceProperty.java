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


  int getMinSpaces() {
    return myMinSpaces;
  }

  int getMaxSpaces() {
    return myMaxSpaces;
  }

  int getMinLineFeeds() {
    return myMinLineFeeds;
  }

  int getMaxLineFeeds() {
    return myMaxLineFeeds;
  }

  boolean isReadOnly(){
    return myIsReadOnly;
  }

  boolean containsLineFeeds() {
    return myMinLineFeeds > 0;
  }
}
