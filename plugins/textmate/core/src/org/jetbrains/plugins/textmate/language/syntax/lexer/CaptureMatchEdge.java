package org.jetbrains.plugins.textmate.language.syntax.lexer;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public final class CaptureMatchEdge {
  public enum Type {
    START, END
  }

  public static final Comparator<CaptureMatchEdge> OFFSET_ORDERING =
    Comparator.comparingInt((CaptureMatchEdge o) -> o.offset)
      .thenComparing(o -> o.group)
      .thenComparing(o -> o.type);

  public final int offset;
  public final int group;
  public final Type type;
  public final CharSequence selectorName;

  CaptureMatchEdge(final int offset, final int group, final Type type, @NotNull final CharSequence selectorName) {
    this.offset = offset;
    this.group = group;
    this.type = type;
    this.selectorName = selectorName;
  }
}
