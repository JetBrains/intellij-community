package org.jetbrains.debugger.memory.tracking;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class IdBasedTracking extends InstanceTrackingStrategy {
  private long myLastId = -1;
  public IdBasedTracking(@NotNull List<ObjectReference> initialInstances) {
    myLastId = initialInstances.stream().map(ObjectReference::uniqueID).max(Long::compare).orElse(-1L);
  }

  @NotNull
  @Override
  protected List<ObjectReference> updateImpl(@NotNull List<ObjectReference> references) {
    return references.stream().filter(reference -> reference.uniqueID() > myLastId).collect(Collectors.toList());
  }
}
