package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

abstract class StructurePath {
  static final StructurePath EMPTY = new StructurePath() {};
  
  static class ChildPath extends StructurePath {
    final StructurePath parent;
    final int childIndex;

    ChildPath(StructurePath parent, int childIndex) {
      this.parent = parent;
      this.childIndex = childIndex;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ChildPath)) return false;
      ChildPath path = (ChildPath)o;
      return childIndex == path.childIndex && Objects.equals(parent, path.parent);
    }

    @Override
    public int hashCode() {
      return Objects.hash(parent, childIndex);
    }
  }

  @Override
  public String toString() {
    return getIndicesFromRoot().toString();
  }

  List<Integer> getIndicesFromRoot() {
    List<Integer> result = new ArrayList<>();
    StructurePath each = this;
    while (each instanceof ChildPath) {
      result.add(((ChildPath)each).childIndex);
      each = ((ChildPath)each).parent;
    }
    Collections.reverse(result);
    return result;
  }

  @NotNull
  StructureNode applyShrink(@NotNull StructureNode source, @NotNull Shrink.ElementaryShrink shrink) {
    List<Integer> indicesFromRoot = getIndicesFromRoot();
    return (StructureNode)new Object() {
      private StructureElement expandShrinks(StructureElement source, int depth) {
        if (depth == indicesFromRoot.size()) {
          return shrink.shrinkNode(source);
        }
        int index = indicesFromRoot.get(depth);
        List<StructureElement> children = new ArrayList<>(((StructureNode)source).children);
        children.set(index, expandShrinks(children.get(index), depth + 1));
        return new StructureNode(children);
      }
    }.expandShrinks(source, 0);
  }

  private StructureElement findNode(StructureElement element) {
    for (Integer index : getIndicesFromRoot()) {
      element = ((StructureNode)element).children.get(index);
    }
    return element;
  }

  @NotNull
  Collection<ShrinkAction> expandShrinks(@NotNull StructureNode source, @NotNull Shrink.ShrinkChildren shrinkChildren) {
    Collection<ShrinkAction> actions = shrinkChildren.expandShrinks((StructureNode)findNode(source));
    return actions.stream().map(a -> new ShrinkAction(prependPath(a.path), a.shrink)).collect(Collectors.toList());
  }

  private StructurePath prependPath(StructurePath path) {
    StructurePath result = this;
    for (Integer integer : path.getIndicesFromRoot()) {
      result = new ChildPath(result, integer);
    }
    return result;
  }
}