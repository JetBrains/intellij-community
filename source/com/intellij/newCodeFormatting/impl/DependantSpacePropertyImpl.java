package com.intellij.newCodeFormatting.impl;

import com.intellij.openapi.util.TextRange;

public class DependantSpacePropertyImpl extends SpacePropertyImpl{
  private final TextRange myDependance;
  private boolean myDependanceContainsLF;

  public DependantSpacePropertyImpl(final int minSpaces,
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
    myDependanceContainsLF = formatter.containsLineFeeds(myDependance);
  }

  public TextRange getDependancy() {
    return myDependance;
  }
}
