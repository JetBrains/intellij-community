package slowCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.function.Function;

/**
 * @author peter
 */
class ShrinkRunner {

  @Nullable
  static <T> T findShrink(@NotNull StructureNode element, Function<StructureNode, T> shrinkProcessor) {
    LinkedList<ShrinkAction> queue = new LinkedList<>();
    for (Shrink shrink : element.shrink()) {
      queue.add(new ShrinkAction(StructurePath.EMPTY, shrink));
    }
    
    while (true) {
      ShrinkAction action = queue.poll();
      if (action == null) return null;
      
      if (action.shrink instanceof Shrink.ElementaryShrink) {
        StructureNode shrank = action.path.applyShrink(element, (Shrink.ElementaryShrink)action.shrink);
        T result = shrinkProcessor.apply(shrank);
        if (result != null) {
          return result;
        }
      } else {
        queue.addAll(0, action.path.expandShrinks(element, (Shrink.ShrinkChildren)action.shrink));
      }
    }
  }
  
}
