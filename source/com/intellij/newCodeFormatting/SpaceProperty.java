package com.intellij.newCodeFormatting;

public class SpaceProperty {
  private final int myMinSpaces;
  private final int myMaxSpaces;
  private final int myMinLineFeeds;
  private final int myMaxLineFeeds;

  public SpaceProperty(final int minSpaces, final int maxSpaces, final int minLineFeeds, final int maxLineFeeds) {
    myMinSpaces = minSpaces;
    myMaxSpaces = maxSpaces;
    myMinLineFeeds = minLineFeeds;
    myMaxLineFeeds = maxLineFeeds;
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
}
