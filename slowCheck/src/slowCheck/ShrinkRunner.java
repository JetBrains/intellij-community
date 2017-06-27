package slowCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * @author peter
 */
class ShrinkRunner {
  private final Set<ShrinkAction> unsuccessful = new HashSet<>();

  @Nullable
  <T> T findShrink(@NotNull StructureNode element, Function<StructureNode, T> shrinkProcessor) {
    List<ShrinkAction> postponed = new ArrayList<>();
    
    LinkedList<ShrinkAction> queue = new LinkedList<>();
    for (Shrink shrink : element.shrink()) {
      queue.add(new ShrinkAction(StructurePath.EMPTY, shrink));
    }
    
    while (true) {
      if (queue.isEmpty() && postponed != null) {
        queue.addAll(postponed);
        postponed = null;
      }
      
      ShrinkAction action = queue.poll();
      if (action == null) return null;

      if (action.shrink instanceof Shrink.ElementaryShrink) {
        if (postponed != null && !unsuccessful.add(action)) {
          postponed.add(action);
          continue;
        }

        StructureNode shrank = action.path.applyShrink(element, (Shrink.ElementaryShrink)action.shrink);
        T result = shrinkProcessor.apply(shrank);
        if (result != null) {
          unsuccessful.remove(action);
          return result;
        }
      } else {
        queue.addAll(0, action.path.expandShrinks(element, (Shrink.ShrinkChildren)action.shrink));
      }
    }
  }
  
}
