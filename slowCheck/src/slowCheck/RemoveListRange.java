package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class RemoveListRange implements Shrink.ElementaryShrink {
  private final int start;
  private final int end;

  RemoveListRange(int start, int end) {
    this.start = start;
    this.end = end;
  }

  @NotNull
  @Override
  public StructureElement shrinkNode(@NotNull StructureElement source) {
    List<StructureElement> original = ((StructureNode)source).children;

    int newSize = original.size() - (end - start) - 1;

    List<StructureElement> list = new ArrayList<>(newSize + 1);
    list.add(new IntData(newSize, IntDistribution.uniform(0, newSize)));
    list.addAll(original.subList(1, start + 1));
    list.addAll(original.subList(end + 1, original.size()));

    return new StructureNode(list);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RemoveListRange)) return false;
    RemoveListRange range = (RemoveListRange)o;
    return start == range.start && end == range.end;
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, end);
  }

  @Override
  public String toString() {
    return "RemoveListRange{" + + start + ", " + end + '}';
  }
}
