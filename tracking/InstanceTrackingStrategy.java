package org.jetbrains.debugger.memory.tracking;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.debugger.memory.component.InstancesTracker.*;

abstract class InstanceTrackingStrategy {
  @NotNull
  private List<ObjectReference> myNewInstances = new ArrayList<>();

  public static InstanceTrackingStrategy create(@NotNull TrackingType type, @NotNull List<ObjectReference> init) {
    switch (type) {
      case IDENTITY:
        return new IdentityBasedTracking(init);
      case HASH:
        return new HashBasedTracking(init);
      case RETAIN:
        return new RetainReferencesTracking(init);
    }

    throw new UnsupportedOperationException("Such TrackingType not found");
  }

  @NotNull
  final List<ObjectReference> getNewInstances() {
    return Collections.unmodifiableList(myNewInstances);
  }

  final void update(@NotNull List<ObjectReference> references) {
    myNewInstances = updateImpl(references);
  }

  @NotNull
  protected abstract List<ObjectReference> updateImpl(@NotNull List<ObjectReference> references);
}
