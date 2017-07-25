package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author peter
 */
interface StructureElement {
  void shrink(Predicate<StructureElement> suitable);
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
  public void shrink(Predicate<StructureElement> suitable) {
    if (shrinkProhibited) return;

    List<StructureElement> children = this.children;
    boolean isList = isList();
    if (isList) {
      children = shrinkList(suitable, children);
    }

    for (int i = isList ? 1 : 0; i < children.size(); i++) {
      children = shrinkChild(suitable, children, i);
    }
  }

  private static List<StructureElement> shrinkChild(Predicate<StructureElement> suitable, List<StructureElement> children, int index) {
    List<StructureElement> result = new ArrayList<>(children);
    children.get(index).shrink(less -> {
      List<StructureElement> copy = new ArrayList<>(result);
      copy.set(index, less);
      if (suitable.test(new StructureNode(copy))) {
        result.set(index, less);
        return true;
      }
      return false;
    });
    return result;
  }

  private static List<StructureElement> shrinkList(Predicate<StructureElement> suitable, List<StructureElement> listChildren) {
    int start = 1;
    int length = 1;
    int limit = listChildren.size();
    while (limit > 0) {
      int lastSuccessfulRemove = -1;
      while (start < limit && start < listChildren.size()) {
        StructureNode less = removeRange(listChildren, start, length);
        if (suitable.test(less)) {
          listChildren = less.children;
          length = Math.min(length * 2, listChildren.size() - start);
          lastSuccessfulRemove = start;
        } else {
          if (length > 1) {
            length /= 2;
          } else {
            start++;
          }
        }
      }
      limit = lastSuccessfulRemove;
    }
    return listChildren;
  }

  @NotNull
  private static StructureNode removeRange(List<StructureElement> listChildren, int start, int length) {
    int newSize = listChildren.size() - length - 1;
    List<StructureElement> lessItems = new ArrayList<>(newSize + 1);
    lessItems.add(new IntData(newSize, IntDistribution.uniform(0, newSize)));
    lessItems.addAll(listChildren.subList(1, start));
    lessItems.addAll(listChildren.subList(start + length, listChildren.size()));
    return new StructureNode(lessItems);
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
  public int hashCode() {
    return children.hashCode();
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
  public void shrink(Predicate<StructureElement> suitable) {
    if (value == 0 || tryInt(0, suitable)) return;

    int value = this.value;
    if (value < 0 && tryInt(-value, suitable)) {
      value = -value;
    }
    while (value != 0 && tryInt(value / 2, suitable)) {
      value /= 2;
    }
  }

  private boolean tryInt(int value, Predicate<StructureElement> suitable) {
    return distribution.isValidValue(value) && suitable.test(new IntData(value, distribution));
  }


  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @Override
  public int hashCode() {
    return value;
  }
}