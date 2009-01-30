package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.changes.local.ChangeListCommand;
import com.intellij.util.EventDispatcher;

import java.util.concurrent.ScheduledExecutorService;

public class DelayedNotificator {
  private final EventDispatcher<ChangeListListener> myDispatcher;
  // this is THE SAME service as is used for change list manager update (i.e. one thread for both processes)
  private final ScheduledExecutorService myService;

  public DelayedNotificator(EventDispatcher<ChangeListListener> dispatcher, final ScheduledExecutorService service) {
    myDispatcher = dispatcher;
    myService = service;
  }

  public void callNotify(final ChangeListCommand command) {
    myService.execute(new Runnable() {
      public void run() {
        command.doNotify(myDispatcher);
      }
    });
  }
}
