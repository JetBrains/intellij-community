package org.jetbrains.plugins.textmate.language.syntax.selector;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class TextMateWeigh implements Comparable<TextMateWeigh> {
  public static final TextMateWeigh ZERO = new TextMateWeigh(0, Priority.LOW);

  public enum Priority {
    LOW,
    NORMAL,
    HIGH,
  }

  public final int weigh;
  public final Priority priority;

  public TextMateWeigh(int weigh, @NotNull Priority priority) {
    this.weigh = weigh;
    this.priority = priority;
  }

  @Override
  public int compareTo(@NotNull TextMateWeigh o) {
    int priorityCompare = Comparing.compare(priority, o.priority);
    if (priorityCompare != 0) {
      return priorityCompare;
    }
    return Comparing.compare(weigh, o.weigh);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TextMateWeigh weigh1 = (TextMateWeigh)o;
    return weigh == weigh1.weigh &&
           priority == weigh1.priority;
  }

  @Override
  public int hashCode() {
    return Objects.hash(weigh, priority);
  }
}
