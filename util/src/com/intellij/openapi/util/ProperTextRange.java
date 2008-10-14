package com.intellij.openapi.util;

/**
 * Text range which asserts its non-negative length
 * @author cdr
 */
public class ProperTextRange extends TextRange {
  public ProperTextRange(int startOffset, int endOffset) {
    super(startOffset, endOffset);
    assertProperRange(this);
  }

  public ProperTextRange(final TextRange range) {
    this(range.getStartOffset(), range.getEndOffset());
  }

  public static void assertProperRange(TextRange range) {
    assert range.getStartOffset() <= range.getEndOffset() : "Invalid range specified "+range;
  }
}
