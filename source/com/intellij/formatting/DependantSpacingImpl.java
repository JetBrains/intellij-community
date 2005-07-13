package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;

public class DependantSpacingImpl extends SpacingImpl {
  private final TextRange myDependance;
  private boolean myDependanceContainsLF;
  private boolean myLineFeedWasUsed = false;

  public DependantSpacingImpl(final int minSpaces,
                              final int maxSpaces,
                              TextRange dependance,
                              final boolean keepLineBreaks,
                              final int keepBlankLines) {
    super(minSpaces, maxSpaces, 0, false, false, keepLineBreaks, keepBlankLines);
    myDependance = dependance;
  }

  int getMinLineFeeds() {
    if (myDependanceContainsLF) {
      return 1;
    }
    else {
      return 0;
    }
  }

  int getMaxLineFeeds() {
    return 1;
  }

  public void refresh(FormatProcessor formatter) {
    myDependanceContainsLF = myLineFeedWasUsed || formatter.containsLineFeeds(myDependance);
  }

  public TextRange getDependancy() {
    return myDependance;
  }

  public void setLFWasUsed(final boolean value) {
    myLineFeedWasUsed = value;
  }

  public boolean wasLFUsed() {
    return myLineFeedWasUsed;
  }
}
