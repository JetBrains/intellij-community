package org.jetbrains.debugger.memory.tracking;

import com.sun.jdi.ObjectReference;
import org.apache.commons.lang.NotImplementedException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HashBasedTracking extends InstanceTrackingStrategy {

  public HashBasedTracking(@NotNull List<ObjectReference> initialInstances) {

  }

  @NotNull
  @Override
  protected List<ObjectReference> updateImpl(@NotNull List<ObjectReference> references) {
    throw new NotImplementedException();
  }
}
