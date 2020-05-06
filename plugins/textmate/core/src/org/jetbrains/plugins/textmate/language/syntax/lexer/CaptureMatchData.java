package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.regex.TextMateRange;

import java.util.Comparator;

public class CaptureMatchData {
  public static final Comparator<CaptureMatchData> START_OFFSET_ORDERING = (o1, o2) -> {
    int result = Integer.compare(o2.range.start, o1.range.start);
    return result != 0 ? result : Integer.compare(o2.group, o1.group);
  };

  public static final Comparator<CaptureMatchData> END_OFFSET_ORDERING = (o1, o2) -> {
    int result = Integer.compare(o2.range.end, o1.range.end);
    return result != 0 ? result : Integer.compare(o1.group, o2.group);
  };

  public final TextMateRange range;
  public final Integer group;
  public final CharSequence selectorName;

  CaptureMatchData(@NotNull final TextMateRange range, @NotNull final Integer group, @NotNull final CharSequence selectorName) {
    this.range = range;
    this.group = group;
    this.selectorName = selectorName;
  }
}
