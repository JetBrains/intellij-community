package org.jetbrains.debugger.memory.tracking;

import com.sun.jdi.ObjectReference;

import java.util.List;

public interface TrackerForNewInstances {
  List<ObjectReference> getNewInstances();
}
