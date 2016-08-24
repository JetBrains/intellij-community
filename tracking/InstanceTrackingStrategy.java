package org.jetbrains.debugger.memory.tracking;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class InstanceTrackingStrategy {
  @NotNull
  private List<ObjectReference> myNewInstances = new ArrayList<>();

  @NotNull
  public final List<ObjectReference> getNewInstances() {
    return Collections.unmodifiableList(myNewInstances);
  }

  public final void update(@NotNull List<ObjectReference> references) {
    myNewInstances = updateImpl(references);
  }

  @NotNull
  protected abstract List<ObjectReference> updateImpl(@NotNull List<ObjectReference> references);
}
