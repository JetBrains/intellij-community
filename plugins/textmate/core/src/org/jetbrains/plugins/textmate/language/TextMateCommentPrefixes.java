package org.jetbrains.plugins.textmate.language;


import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class TextMateCommentPrefixes {
  private final @Nullable String lineCommentPrefix;

  private final @Nullable TextMateBlockCommentPair blockCommentPair;

  public TextMateCommentPrefixes(@Nullable String lineCommentPrefix, @Nullable TextMateBlockCommentPair blockCommentPair) {
    this.lineCommentPrefix = lineCommentPrefix;
    this.blockCommentPair = blockCommentPair;
  }

  public @Nullable String getLineCommentPrefix() {
    return lineCommentPrefix;
  }

  public @Nullable TextMateBlockCommentPair getBlockCommentPair() {
    return blockCommentPair;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TextMateCommentPrefixes prefixes = (TextMateCommentPrefixes)o;

    if (!Objects.equals(lineCommentPrefix, prefixes.lineCommentPrefix))
      return false;
    if (!Objects.equals(blockCommentPair, prefixes.blockCommentPair)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(lineCommentPrefix, blockCommentPair);
  }
}
