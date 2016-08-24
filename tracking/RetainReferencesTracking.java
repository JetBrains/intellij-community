package org.jetbrains.debugger.memory.tracking;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class RetainReferencesTracking extends InstanceTrackingStrategy {
  private final IdentityBasedTracking myIdTracker;
  @SuppressWarnings("unused")
  private List<ObjectReference> myHardRefs;
  RetainReferencesTracking(@NotNull List<ObjectReference> initialInstances) {
    myHardRefs = initialInstances;
    myIdTracker = new IdentityBasedTracking(initialInstances);
  }

  @NotNull
  @Override
  protected List<ObjectReference> updateImpl(@NotNull List<ObjectReference> references) {
    myIdTracker.update(references);
    myHardRefs = references;
    return myIdTracker.getNewInstances();
  }
}
