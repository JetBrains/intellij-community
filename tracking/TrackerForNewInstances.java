package org.jetbrains.debugger.memory.tracking;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.event.InstancesTrackerListener;

import java.util.List;

public interface TrackerForNewInstances extends InstancesTrackerListener {
  @NotNull
  List<ObjectReference> getNewInstances();

  int getCount();

  boolean isReady();
}
