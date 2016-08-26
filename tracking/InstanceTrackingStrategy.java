package org.jetbrains.debugger.memory.tracking;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class InstanceTrackingStrategy {
  @NotNull
  private List<ObjectReference> myNewInstances = new ArrayList<>();

  public static InstanceTrackingStrategy create(@NotNull ReferenceType referenceType,
                                                @NotNull SuspendContextImpl suspendContext,
                                                @NotNull TrackingType type,
                                                @NotNull List<ObjectReference> init) {
    switch (type) {
      case IDENTITY:
        return new IdentityBasedTracking(init);
      case HASH:
        return new HashBasedTracking(suspendContext, referenceType, init);
      case RETAIN:
        return new RetainReferencesTracking(init);
    }

    throw new UnsupportedOperationException("Such TrackingType not found");
  }

  @NotNull
  public final List<ObjectReference> getNewInstances() {
    return Collections.unmodifiableList(myNewInstances);
  }

  public final void update(@NotNull SuspendContextImpl suspendContext, @NotNull List<ObjectReference> references) {
    myNewInstances = updateImpl(suspendContext, references);
  }

  @NotNull
  protected abstract List<ObjectReference> updateImpl(@NotNull SuspendContextImpl suspendContext,
                                                      @NotNull List<ObjectReference> references);
}
