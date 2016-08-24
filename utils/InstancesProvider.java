package org.jetbrains.debugger.memory.utils;

import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface InstancesProvider {
  @NotNull
  List<ObjectReference> getInstances(int limit);
}
