package slowCheck;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author peter
 */
class ShrinkAction {
  final StructurePath path;
  final Shrink shrink;

  ShrinkAction(@NotNull StructurePath path, @NotNull Shrink shrink) {
    this.path = path;
    this.shrink = shrink;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ShrinkAction)) return false;
    ShrinkAction action = (ShrinkAction)o;
    return Objects.equals(path, action.path) &&
           Objects.equals(shrink, action.shrink);
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, shrink);
  }

  @Override
  public String toString() {
    return "ShrinkAction{" +
           "path=" + path +
           ", shrink=" + shrink +
           '}';
  }
}

interface Shrink {

  interface ElementaryShrink extends Shrink {
    @NotNull
    StructureElement shrinkNode(@NotNull StructureElement source);
  }

  ShrinkChildren SHRINK_ALL_CHILDREN = new ShrinkChildren(true);
  ShrinkChildren SHRINK_LIST_ELEMENTS = new ShrinkChildren(false);

  class ShrinkChildren implements Shrink {
    private final boolean myShrinkInts;

    private ShrinkChildren(boolean shrinkInts) {
      myShrinkInts = shrinkInts;
    }

    @NotNull
    public List<ShrinkAction> expandShrinks(@NotNull StructureNode source) {
      List<ShrinkAction> result = new ArrayList<>();
      for (int i = 0; i < source.children.size(); i++) {
        StructureElement child = source.children.get(i);
        if (child instanceof IntData && !myShrinkInts) continue;

        for (Shrink shrink : child.shrink()) {
          result.add(new ShrinkAction(new StructurePath.ChildPath(StructurePath.EMPTY, i), shrink));
        }
      }
      return result;
    }

  }

}

