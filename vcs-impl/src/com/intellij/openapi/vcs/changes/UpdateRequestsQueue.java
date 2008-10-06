package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ChangeListManager updates scheduler.
 * Tries to zip several update requests into one (if starts and see several requests in the queue)
 * own inner synchronization
 */
public class UpdateRequestsQueue {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.UpdateRequestsQueue");
  private final Project myProject;
  private final ScheduledExecutorService myExecutor;
  private final Consumer<Boolean> myAction;
  private final Object myLock;
  private volatile boolean myStarted;
  private volatile boolean myStopped;

  private ScheduledFuture<?> myTask;
  private final List<Runnable> myWaitingUpdateCompletionQueue;
  private final ProjectLevelVcsManager myPlVcsManager;
  private boolean myUpdateUnversionedRequested;

  public UpdateRequestsQueue(final Project project, final ScheduledExecutorService executor, final Consumer<Boolean> action) {
    myExecutor = executor;
    myAction = action;
    myProject = project;
    myPlVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myLock = new Object();
    myWaitingUpdateCompletionQueue = new ArrayList<Runnable>();
    // not initialized
    myStarted = false;
    myStopped = false;
    myUpdateUnversionedRequested = false;
  }

  public void initialized() {
    LOG.debug("Initialized for project: " + myProject.getName());
    myStarted = true;
  }

  public boolean isStopped() {
    return myStopped;
  }

  public void schedule(final boolean updateUnversionedFiles) {
    synchronized (myLock) {
      if (! myStarted && ApplicationManager.getApplication().isUnitTestMode()) return;

      if (! myStopped) {
        if (myTask == null) {
          final MyRunnable runnable = new MyRunnable();
          myTask = myExecutor.schedule(runnable, 300, TimeUnit.MILLISECONDS);
          LOG.debug("Scheduled for project: " + myProject.getName() + ", runnable: " + runnable.hashCode());
          myUpdateUnversionedRequested |= updateUnversionedFiles;
        } else if (updateUnversionedFiles && (! myUpdateUnversionedRequested)) {
          myUpdateUnversionedRequested = true;
        }
      }
    }
  }

  public void stop() {
    LOG.debug("Calling stop for project: " + myProject.getName());
    final List<Runnable> waiters = new ArrayList<Runnable>(myWaitingUpdateCompletionQueue.size());
    synchronized (myLock) {
      myStopped = true;
      if (myTask != null) {
        myTask.cancel(false);
        myTask = null;
      }
      waiters.addAll(myWaitingUpdateCompletionQueue);
      myWaitingUpdateCompletionQueue.clear();
    }
    LOG.debug("Calling runnables in stop for project: " + myProject.getName());
    // do not run under lock
    for (Runnable runnable : waiters) {
      runnable.run();
    }
    LOG.debug("Stop finished for project: " + myProject.getName());
  }

  private static class CallbackData {
    private final Runnable myCallback;
    private final Runnable myWrapperStarter;

    private CallbackData(@NotNull final Runnable callback, @Nullable final Runnable wrapperStarter) {
      myCallback = callback;
      myWrapperStarter = wrapperStarter;
    }
  }

  public void invokeAfterUpdate(final Runnable afterUpdate, final boolean cancellable, final boolean silently, final String title, final boolean synchronously) {
    LOG.debug("invokeAfterUpdate for project: " + myProject.getName());
    final CallbackData data = createCallbackWrapperRunnable(afterUpdate, cancellable, silently, title, synchronously);
    synchronized (myLock) {
      if (! myStopped) {
        myWaitingUpdateCompletionQueue.add(data.myCallback);
        schedule(true);
      }
    }
    // do not run under lock; stopped cannot be switched into not stopped - can check without lock
    if (myStopped) {
      LOG.debug("invokeAfterUpdate: stopped, invoke right now for project: " + myProject.getName());
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          afterUpdate.run();
        }
      });
      return;
    } else {
      // invoke progress if needed
      if (data.myWrapperStarter != null) {
        data.myWrapperStarter.run();
      }
    }
    LOG.debug("invokeAfterUpdate: exit for project: " + myProject.getName());
  }

  private CallbackData createCallbackWrapperRunnable(final Runnable afterUpdate, final boolean cancellable, final boolean silently, final String title, final boolean synchronously) {
    if (silently) {
      return new CallbackData(new Runnable() {
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              LOG.debug("invokeAfterUpdate: silent wrapper called for project: " + myProject.getName());
              if (myProject.isDisposed()) return;
              afterUpdate.run();
              ChangesViewManager.getInstance(myProject).refreshView();
            }
          });
        }
      }, null);
    } else {
      if (synchronously) {
        final Waiter waiter = new Waiter(myProject, afterUpdate);
        return new CallbackData(
          new Runnable() {
            public void run() {
              LOG.debug("invokeAfterUpdate: NOT silent SYNCHRONOUS wrapper called for project: " + myProject.getName());
              waiter.done();
            }
          }, new Runnable() {
            public void run() {
              ProgressManager.getInstance().runProcessWithProgressSynchronously(waiter,
                VcsBundle.message("change.list.manager.wait.lists.synchronization", title), false, myProject);
            }
          }
        );
      } else {
        final FictiveBackgroundable fictiveBackgroundable = new FictiveBackgroundable(myProject, afterUpdate, cancellable, title);
        return new CallbackData(
          new Runnable() {
            public void run() {
              LOG.debug("invokeAfterUpdate: NOT silent wrapper called for project: " + myProject.getName());
              fictiveBackgroundable.done();
            }
          }, new Runnable() {
            public void run() {
              ProgressManager.getInstance().run(fictiveBackgroundable);
            }
          });
      }
    }
  }

  private class MyRunnable implements Runnable {
    public void run() {
      myTask = null;

      boolean updateUnversioned;
      final List<Runnable> copy = new ArrayList<Runnable>(myWaitingUpdateCompletionQueue.size());

      synchronized (myLock) {
        if ((! myStopped) && ((! myStarted) || myPlVcsManager.isBackgroundVcsOperationRunning())) {
          LOG.debug("MyRunnable: not started, not stopped, reschedule, project: " + myProject.getName() + ", runnable: " + hashCode());
          // try again after time
          schedule(myUpdateUnversionedRequested);
          return;
        }
        if (myStopped) {
          LOG.debug("MyRunnable: STOPPED, project: " + myProject.getName() + ", runnable: " + hashCode());
          return;
        }

        copy.addAll(myWaitingUpdateCompletionQueue);
        // take it under lock
        updateUnversioned = myUpdateUnversionedRequested;
        // for concurrent schedules to tigger flag correctly
        myUpdateUnversionedRequested = false;
      }

      try {
        LOG.debug("MyRunnable: INVOKE, project: " + myProject.getName() + ", runnable: " + hashCode());
        myAction.consume(updateUnversioned);
        LOG.debug("MyRunnable: invokeD, project: " + myProject.getName() + ", runnable: " + hashCode());
      } finally {
        synchronized (myLock) {
          LOG.debug("MyRunnable: delete executed, project: " + myProject.getName() + ", runnable: " + hashCode());
          if (! copy.isEmpty()) {
            myWaitingUpdateCompletionQueue.removeAll(copy);
          }
        }
        // do not run under lock
        for (Runnable runnable : copy) {
          runnable.run();
        }
        LOG.debug("MyRunnable: Runnables executed, project: " + myProject.getName() + ", runnable: " + hashCode());
      }
    }
  }
}
