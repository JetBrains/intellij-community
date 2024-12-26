package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.regex.TextMateRange;

public final class CaptureMatchData {
  public final TextMateRange range;
  public final int group;
  public final CharSequence selectorName;

  CaptureMatchData(final @NotNull TextMateRange range, final int group, final @NotNull CharSequence selectorName) {
    this.range = range;
    this.group = group;
    this.selectorName = selectorName;
  }
}
