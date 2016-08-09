package org.jetbrains.debugger.memory.utils;

import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

public class SingleAlarmWithMutableDelay extends Alarm {
  private final Runnable myTask;
  private volatile int myDelay;
  public SingleAlarmWithMutableDelay(@NotNull Runnable task) {
    myTask = task;
  }

  public void setDelay(int value) {
    myDelay = value;
  }

  public void cancelAndRequest() {
    if(!isDisposed()) {
      cancelAllRequests();
      addRequest(myTask, myDelay);
    }
  }
}
