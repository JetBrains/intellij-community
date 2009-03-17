package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

/**
 * Text range which asserts its non-negative startOffset and length
 */
public class ProperTextRange extends TextRange {
  public ProperTextRange(int startOffset, int endOffset) {
    super(startOffset, endOffset);
    assertProperRange(this);
  }

  public ProperTextRange(@NotNull TextRange range) {
    this(range.getStartOffset(), range.getEndOffset());
  }

  public static void assertProperRange(@NotNull TextRange range) throws AssertionError {
    assert range.getStartOffset() <= range.getEndOffset() : "Invalid range specified: " + range;
    assert range.getStartOffset() >= 0 : "Negative start offset: " + range;
  }

  @NotNull
  @Override
  public TextRange cutOut(@NotNull TextRange subRange) {
    TextRange range = super.cutOut(subRange);
    assertProperRange(range);
    return range;
  }

  @NotNull
  @Override
  public TextRange shiftRight(int offset) {
    TextRange range = super.shiftRight(offset);
    assertProperRange(range);
    return range;
  }

  @NotNull
  @Override
  public TextRange grown(int lengthDelta) {
    TextRange range = super.grown(lengthDelta);
    assertProperRange(range);
    return range;
  }

  @Override
  public TextRange intersection(@NotNull TextRange textRange) {
    TextRange range = super.intersection(textRange);
    if (range != null) assertProperRange(range);
    return range;
  }

  @NotNull
  @Override
  public TextRange union(@NotNull TextRange textRange) {
    TextRange range = super.union(textRange);
    assertProperRange(range);
    return range;
  }
}
