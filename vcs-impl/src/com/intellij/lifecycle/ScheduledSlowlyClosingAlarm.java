package com.intellij.lifecycle;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledSlowlyClosingAlarm extends SlowlyClosingAlarm {
  ScheduledSlowlyClosingAlarm(@NotNull Project project, @NotNull String name, ExecutorService executor, boolean executorIsShared) {
    super(project, name, executor, executorIsShared);
  }

  public void addRequest(final Runnable runnable, final int delayMillis) {
    if (myExecutorService instanceof ScheduledExecutorService) {
      synchronized (myLock) {
        if (myDisposeStarted) return;
        final MyWrapper wrapper = new MyWrapper(runnable);
        final ScheduledFuture<?> future =
          ((ScheduledExecutorService)myExecutorService).schedule(wrapper, delayMillis, TimeUnit.MILLISECONDS);
        wrapper.setFuture(future);
        myFutureList.add(future);
        debug("request scheduled");
      }
    } else {
      addRequest(runnable);
    }
  }
}
