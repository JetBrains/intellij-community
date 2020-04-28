package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class CaptureMatchData {
  public static final Comparator<CaptureMatchData> START_OFFSET_ORDERING = (o1, o2) -> {
    int result = Integer.compare(o2.range.getStartOffset(), o1.range.getStartOffset());
    return result != 0 ? result : Integer.compare(o2.group, o1.group);
  };

  public static final Comparator<CaptureMatchData> END_OFFSET_ORDERING = (o1, o2) -> {
    int result = Integer.compare(o2.range.getEndOffset(), o1.range.getEndOffset());
    return result != 0 ? result : Integer.compare(o1.group, o2.group);
  };

  public final TextRange range;
  public final Integer group;
  public final CharSequence selectorName;

  CaptureMatchData(@NotNull final TextRange range, @NotNull final Integer group, @NotNull final CharSequence selectorName) {
    this.range = range;
    this.group = group;
    this.selectorName = selectorName;
  }
}
