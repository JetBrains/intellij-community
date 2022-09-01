package org.jetbrains.plugins.textmate.language;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class TextMateBlockCommentPair {
  private final @NotNull String prefix;
  private final @NotNull String suffix;

  public TextMateBlockCommentPair(@NotNull String prefix, @NotNull String suffix) {
    this.prefix = prefix;
    this.suffix = suffix;
  }

  public @NotNull String getPrefix() {
    return prefix;
  }

  public @NotNull String getSuffix() {
    return suffix;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TextMateBlockCommentPair pair = (TextMateBlockCommentPair)o;

    if (!prefix.equals(pair.prefix)) return false;
    if (!suffix.equals(pair.suffix)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefix, suffix);
  }
}
