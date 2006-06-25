package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;

/**
 * @author max
 */
public class LimitedRangeHighlighterIterator implements HighlighterIterator {
  private HighlighterIterator myOriginal;
  private int myStartOffset;
  private int myEndOffset;


  public LimitedRangeHighlighterIterator(final HighlighterIterator original, final int startOffset, final int endOffset) {
    myOriginal = original;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public TextAttributes getTextAttributes() {
    return myOriginal.getTextAttributes();
  }

  public int getStart() {
    return Math.max(myOriginal.getStart(), myStartOffset);
  }

  public int getEnd() {
    return Math.min(myOriginal.getEnd(), myEndOffset);
  }

  public IElementType getTokenType() {
    return myOriginal.getTokenType();
  }

  public void advance() {
    myOriginal.advance();
  }

  public void retreat() {
    myOriginal.retreat();
  }

  public boolean atEnd() {
    return myOriginal.atEnd() || myOriginal.getStart() > myEndOffset || myOriginal.getEnd() <= myStartOffset;
  }
}
