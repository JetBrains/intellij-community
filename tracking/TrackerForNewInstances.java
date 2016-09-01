package org.jetbrains.debugger.memory.tracking;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface TrackerForNewInstances {
  @NotNull
  List<ObjectReference> getNewInstances();
  boolean isReady();
}
