package com.intellij.newCodeFormatting;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;

class WhiteSpace{
  private TextRange myTextRange;
  private int mySpaces;
  private int myLineFeeds;
  private final boolean myIsFirstWhiteSpace;
  private boolean myIsReadOnly;
  private CharSequence myInitial;

  public WhiteSpace(int startOffset, int endOffset, int spaces, int lineFeeds, boolean isFirst) {
    myTextRange = new TextRange(startOffset, endOffset);
    mySpaces = spaces;
    myLineFeeds = lineFeeds;
    myIsFirstWhiteSpace = isFirst;
  }

  public void append(int newEndOffset, FormattingModel model) {
    final int oldEndOffset = myTextRange.getEndOffset();
    if (newEndOffset == oldEndOffset) return;
    int oldLastLine = model.getLineNumber(oldEndOffset);
    int newLine = model.getLineNumber(newEndOffset);
    myTextRange = new TextRange(myTextRange.getStartOffset(), newEndOffset);
    final int lineIncrement = newLine - oldLastLine;
    if (lineIncrement > 0) {
      mySpaces = newEndOffset - model.getLineStartOffset(model.getLineNumber(newEndOffset));
    } else {
      mySpaces += myTextRange.getEndOffset() - oldEndOffset;
    }
    myLineFeeds += lineIncrement;

    myInitial = model.getText(myTextRange);
  }

  public String generateWhiteSpace() {
    return StringUtil.repeatSymbol('\n', myLineFeeds) + StringUtil.repeatSymbol(' ', mySpaces);
  }

  public void setSpaces(final int indent) {
    mySpaces = indent;
  }

  public TextRange getTextRange() {
    return myTextRange;
  }

  public void arrangeSpaces(final SpaceProperty spaceProperty) {
    if (spaceProperty != null && !myIsReadOnly) {
      if (myLineFeeds == 0) {
        if (mySpaces < spaceProperty.getMinSpaces()) {
          mySpaces = spaceProperty.getMinSpaces();
        }
        if (mySpaces > spaceProperty.getMaxSpaces()){
          mySpaces = spaceProperty.getMaxSpaces();
        }
      }
    }

  }

  public void arrangeLineFeeds(final SpaceProperty spaceProperty) {
    if (spaceProperty != null && !myIsReadOnly) {
      if (myLineFeeds < spaceProperty.getMinLineFeeds()) {
        myLineFeeds = spaceProperty.getMinLineFeeds();
      }

      if (myLineFeeds > spaceProperty.getMaxLineFeeds()) {
        myLineFeeds = spaceProperty.getMaxLineFeeds();
      }
    }
  }

  public int getLineFeeds() {
    return myLineFeeds;
  }

  public boolean containsLineFeeds() {
    return myIsFirstWhiteSpace || myLineFeeds > 0;
  }

  public int getSpaces() {
    return mySpaces;
  }

  public void ensureLineFeed() {
    if (!containsLineFeeds() && !myIsReadOnly) {
      myLineFeeds = 1;
      mySpaces = 0;
    }
  }

  public boolean isReadOnly() {
    return myIsReadOnly;
  }

  public void setReadOnly() {
    myIsReadOnly = true;
  }

  public boolean equals(String ws) {
    if (myInitial == null) return false;
    return myInitial.equals(ws);
  }
}

