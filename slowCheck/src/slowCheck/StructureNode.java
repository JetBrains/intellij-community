package slowCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author peter
 */
interface StructureElement {
  Stream<? extends StructureElement> shrink();
}

class StructureNode implements StructureElement {
  private final List<StructureElement> children;
  private final int lastModifiedChild;
  boolean shrinkProhibited;

  StructureNode() {
    children = new ArrayList<>();
    lastModifiedChild = -1;
  }

  StructureNode(Stream<StructureElement> children, int lastModifiedChild) {
    this.children = children.collect(Collectors.toList());
    this.lastModifiedChild = lastModifiedChild;
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

  private Stream<StructureNode> shrinkChildren(boolean shrinkIntChildren) {
    return indices(children.size(), lastModifiedChild)
      .mapToObj(i -> shrinkChild(shrinkIntChildren, children.get(i)).map(replacement -> replaceChild(i, replacement)))
      .flatMap(s -> s);
  }

  private StructureNode replaceChild(int i, StructureElement replacement) {
    List<StructureElement> list = new ArrayList<>(children);
    list.set(i, replacement);
    return new StructureNode(list.stream(), i);
  }

  void removeLastChild(StructureNode node) {
    if (children.isEmpty() || children.get(children.size() - 1) != node) {
      throw new IllegalStateException("Last sub-structure changed");
    }
    children.remove(children.size() - 1);
  }

  private static Stream<? extends StructureElement> shrinkChild(boolean shrinkIntChildren, StructureElement child) {
    if (child instanceof IntData && !shrinkIntChildren) {
      return Stream.empty();
    }
    return child.shrink();
  }

  private Stream<StructureNode> shrinkList(List<StructureNode> elements) {
    Stream<StructureNode> removeEachItem = indices(elements.size(), lastModifiedChild - 1).mapToObj(i -> {
      List<StructureNode> list = new ArrayList<>(elements);
      list.remove(i);
      return createList(list, i - 1);
    });
    if (elements.size() > 4) {
      Stream<StructureNode> halves =
        Stream.of(createList(elements.subList(0, elements.size() / 2), -1),
                  createList(elements.subList(elements.size() / 2, elements.size()), -1));
      return Stream.concat(halves, removeEachItem);
    }
    return removeEachItem;
  }

  private static IntStream indices(int size, int lastModified) {
    if (lastModified > 0) {
      return IntStream.concat(IntStream.range(lastModified, size), IntStream.range(0, lastModified));
    }
    
    return IntStream.range(0, size);
  }

  @NotNull
  public Stream<StructureNode> shrink() {
    if (shrinkProhibited) return Stream.empty();
    
    List<StructureNode> listChildren = asList();
    Stream<StructureNode> listVariants = listChildren != null ? shrinkList(listChildren) : Stream.empty();
    return Stream.concat(listVariants, shrinkChildren(listChildren == null));
  }

  @Nullable
  private List<StructureNode> asList() {
    if (!children.isEmpty() &&
        children.get(0) instanceof IntData && ((IntData)children.get(0)).value == children.size() - 1) {
      List<StructureNode> result = new ArrayList<>();
      for (int i = 1; i < children.size(); i++) {
        Object child = children.get(i);
        if (!(child instanceof StructureNode)) return null;
        result.add((StructureNode)child);
      }
      return result;
    }
    return null;
  }

  private static StructureNode createList(List<StructureNode> list, int modifiedIndex) {
    return new StructureNode(Stream.concat(Stream.of(new IntData(list.size(), IntDistribution.uniform(0, list.size()))), list.stream()), modifiedIndex + 1);
  }

  @Override
  public String toString() {
    return "(" + children.stream().map(Object::toString).collect(Collectors.joining(", ")) + ")";
  }

}

class IntData implements StructureElement {
  final int value;
  final IntDistribution distribution;

  IntData(int value, IntDistribution distribution) {
    this.value = value;
    this.distribution = distribution;
  }

  @Override
  public Stream<IntData> shrink() {
    if (value == 0) return Stream.empty();

    Set<IntData> builder = new LinkedHashSet<>();
    if (value < 0 && distribution.isValidValue(-value)) {
      builder.add(new IntData(-value, distribution));
    }
    if (distribution.isValidValue(value / 2)) {
      builder.add(new IntData(value / 2, distribution));
    }
    return builder.stream();
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}