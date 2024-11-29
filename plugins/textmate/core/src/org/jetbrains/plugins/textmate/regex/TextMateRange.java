package org.jetbrains.plugins.textmate.regex;

import java.util.Objects;

public final class TextMateRange {
  public final int start;
  public final int end;

  public TextMateRange(int start, int end) {
    this.start = start;
    this.end = end;
  }

  public boolean isEmpty() {
    return start == end;
  }

  public int getLength() {
    return end - start;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TextMateRange range = (TextMateRange)o;
    return start == range.start &&
           end == range.end;
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, end);
  }

  @Override
  public String toString() {
    return "TextMateRange[" + start + ", " + end + ']';
  }
}
