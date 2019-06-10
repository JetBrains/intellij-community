package org.jetbrains.plugins.textmate.regex;

import org.joni.Matcher;
import org.joni.Option;

public class Searcher {
  private final Matcher myMatcher;
  private final String myString;
  private int currentPosition = 0;
  private int currentCharPosition = 0;
  private final byte[] myStringBytes;

  Searcher(String string, byte[] stringBytes, Matcher matcher) {
    myString = string;
    myStringBytes = stringBytes;
    myMatcher = matcher;
  }

  public boolean search() {
    if (currentPosition < 0) {
      return false;
    }
    final int searchResult = myMatcher.search(currentPosition, myStringBytes.length, Option.CAPTURE_GROUP);
    if (searchResult > -1) {
      setCurrentPosition(myMatcher.getEagerRegion().end[0]);
    }
    else {
      setCurrentPosition(searchResult);
    }
    return currentPosition > -1;
  }

  private void setCurrentPosition(int currentPosition) {
    this.currentPosition = currentPosition;
    currentCharPosition = currentPosition > 0
                          ? RegexUtil.charOffsetByByteOffset(myStringBytes, currentPosition)
                          : currentPosition;
  }

  public int getCurrentCharPosition() {
    return currentCharPosition;
  }

  public MatchData getCurrentMatchData() {
    return MatchData.fromRegion(myString, myStringBytes, myMatcher.getEagerRegion());
  }
}
