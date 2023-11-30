package org.jetbrains.plugins.textmate.regex;

import org.joni.Matcher;
import org.joni.Option;

public final class Searcher {
  private final Matcher myMatcher;
  private int currentPosition = 0;
  private int currentCharPosition = 0;
  private final byte[] myStringBytes;

  Searcher(byte[] stringBytes, Matcher matcher) {
    myStringBytes = stringBytes;
    myMatcher = matcher;
  }

  public boolean search() {
    if (currentPosition < 0) {
      return false;
    }
    final int searchResult = myMatcher.search(currentPosition, myStringBytes.length, Option.CAPTURE_GROUP);
    if (searchResult > -1) {
      setCurrentPosition(myMatcher.getEagerRegion().getEnd(0));
    }
    else {
      setCurrentPosition(searchResult);
    }
    return currentPosition > -1;
  }

  private void setCurrentPosition(int currentPosition) {
    this.currentPosition = currentPosition;
    currentCharPosition = currentPosition > 0
                          ? RegexUtil.codePointOffsetByByteOffset(myStringBytes, currentPosition)
                          : currentPosition;
  }

  public int getCurrentCharPosition() {
    return currentCharPosition;
  }

  public MatchData getCurrentMatchData() {
    return MatchData.fromRegion(myMatcher.getEagerRegion());
  }
}
