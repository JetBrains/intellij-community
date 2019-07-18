package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

class CaptureMatchData {
  public static final Comparator<CaptureMatchData> START_OFFSET_ORDERING = (o1, o2) -> {
    int result = Comparing.compare(o2.offset.getStartOffset(), o1.offset.getStartOffset());
    return result != 0 ? result : Comparing.compare(o2.group, o1.group);
  };

  public static final Comparator<CaptureMatchData> END_OFFSET_ORDERING = (o1, o2) -> {
    int result = Comparing.compare(o2.offset.getEndOffset(), o1.offset.getEndOffset());
    return result != 0 ? result : Comparing.compare(o1.group, o2.group);
  };

  public final TextRange offset;
  public final Integer group;
  public final String selectorName;

  CaptureMatchData(@NotNull final TextRange offset, @NotNull final Integer group, @NotNull final String selectorName) {
    this.offset = offset;
    this.group = group;
    this.selectorName = selectorName;
  }
}
