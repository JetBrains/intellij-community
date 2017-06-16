package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author peter
 */
interface StructureElement {
  Collection<? extends Shrink> shrink();
}

class StructureNode implements StructureElement {
  final List<StructureElement> children;
  boolean shrinkProhibited;

  StructureNode() {
    this(new ArrayList<>());
  }

  StructureNode(List<StructureElement> children) {
    this.children = children;
  }

  Iterator<StructureElement> childrenIterator() {
    return children.iterator();
  }

  void addChild(StructureElement child) {
    children.add(child);
  }

  StructureNode subStructure() {
    StructureNode e = new StructureNode();
    addChild(e);
    return e;
  }

  void removeLastChild(StructureNode node) {
    if (children.isEmpty() || children.get(children.size() - 1) != node) {
      throw new IllegalStateException("Last sub-structure changed");
    }
    children.remove(children.size() - 1);
  }

  @Override
  public List<Shrink> shrink() {
    if (shrinkProhibited) return Collections.emptyList();

    return isList() ? shrinkList(children.size() - 1) : Collections.singletonList(Shrink.SHRINK_ALL_CHILDREN);
  }

  private static List<Shrink> shrinkList(int size) {
    List<Shrink> result = new ArrayList<>();
    if (size > 4) {
      result.add(new RemoveListRange(size / 2, size));
      result.add(new RemoveListRange(0, size / 2));
    }
    for (int i = 0; i < size; i++) {
      result.add(new RemoveListRange(i, i + 1));
    }
    result.add(Shrink.SHRINK_LIST_ELEMENTS);
    return result;
  }

  private boolean isList() {
    if (!children.isEmpty() &&
        children.get(0) instanceof IntData && ((IntData)children.get(0)).value == children.size() - 1) {
      for (int i = 1; i < children.size(); i++) {
        if (!(children.get(i) instanceof StructureNode)) return false;
      }
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return "(" + children.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
  }

}

class IntData implements StructureElement, Shrink.ElementaryShrink {
  final int value;
  final IntDistribution distribution;

  IntData(int value, IntDistribution distribution) {
    this.value = value;
    this.distribution = distribution;
  }

  @Override
  public Collection<IntData> shrink() {
    if (value == 0) return Collections.emptyList();

    Set<IntData> builder = new LinkedHashSet<>();
    if (value < 0 && distribution.isValidValue(-value)) {
      builder.add(new IntData(-value, distribution));
    }
    if (distribution.isValidValue(value / 2)) {
      builder.add(new IntData(value / 2, distribution));
    }
    return builder;
  }

  @NotNull
  @Override
  public StructureElement shrinkNode(@NotNull StructureElement source) {
    return this;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}