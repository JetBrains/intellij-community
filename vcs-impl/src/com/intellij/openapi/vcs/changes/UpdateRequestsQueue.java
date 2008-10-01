package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
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
  private final Project myProject;
  private final ScheduledExecutorService myExecutor;
  private final Consumer<Boolean> myAction;
  private final Object myLock;
  private volatile boolean myStarted;
  private volatile boolean myStopped;

  private final LinkedList<ScheduledData> mySentRequests;
  private final List<Runnable> myWaitingUpdateCompletionQueue;
  private final ProjectLevelVcsManager myPlVcsManager;

  public UpdateRequestsQueue(final Project project, final ScheduledExecutorService executor, final Consumer<Boolean> action) {
    myExecutor = executor;
    myAction = action;
    myProject = project;
    myPlVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myLock = new Object();
    mySentRequests = new LinkedList<ScheduledData>();
    myWaitingUpdateCompletionQueue = new ArrayList<Runnable>();
    // not initialized
    myStarted = false;
    myStopped = false;
  }

  public void initialized() {
    myStarted = true;
  }

  public boolean isStopped() {
    return myStopped;
  }

  private boolean updateUnversionedFilesEngaged = false;
  private boolean updateWithoutUnversionedFilesEngaged = false;

  public void schedule(final boolean updateUnversionedFiles) {
    synchronized (myLock) {
      if (! myStarted && ApplicationManager.getApplication().isUnitTestMode()) return;

      if (! myStopped) {
        if (updateUnversionedFiles) {
          if (updateUnversionedFilesEngaged) return;
          updateUnversionedFilesEngaged = true;
        }
        else {
          if (updateWithoutUnversionedFilesEngaged) return;
          updateWithoutUnversionedFilesEngaged = true;
        }

        final MyRunnable runnable = new MyRunnable(myAction, updateUnversionedFiles);
        final ScheduledFuture<?> future = myExecutor.schedule(runnable, 300, TimeUnit.MILLISECONDS);
        final ScheduledData data = new ScheduledData(runnable, future);
        mySentRequests.add(data);
      }
    }
  }

  public void stop() {
    final List<Runnable> waiters = new ArrayList<Runnable>(myWaitingUpdateCompletionQueue.size());
    synchronized (myLock) {
      myStopped = true;
      for (ScheduledData sentRequest : mySentRequests) {
        sentRequest.cancel();
      }
      mySentRequests.clear();
      waiters.addAll(myWaitingUpdateCompletionQueue);
      myWaitingUpdateCompletionQueue.clear();
    }
    // do not run under lock
    for (Runnable runnable : waiters) {
      runnable.run();
    }
  }

  public void invokeAfterUpdate(final Runnable afterUpdate, final boolean cancellable, final boolean silently, final String title) {
    final Runnable runnable = createCallbackWrapperRunnable(afterUpdate, cancellable, silently, title);
    synchronized (myLock) {
      if (! myStopped) {
        myWaitingUpdateCompletionQueue.add(runnable);
        schedule(true);
      }
    }
    // do not run under lock
    if (myStopped) {
      runnable.run();
      return;
    }
  }

  private Runnable createCallbackWrapperRunnable(final Runnable afterUpdate, final boolean cancellable, final boolean silently, final String title) {
    if (silently) {
      return new Runnable() {
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (myProject.isDisposed()) return;
              afterUpdate.run();
              ChangesViewManager.getInstance(myProject).refreshView();
            }
          });
        }
      };
    } else {
      final FictiveBackgroundable fictiveBackgroundable = new FictiveBackgroundable(myProject, afterUpdate, cancellable, title);
      ProgressManager.getInstance().run(fictiveBackgroundable);
      return new Runnable() {
        public void run() {
          fictiveBackgroundable.done();
        }
      };
    }
  }

  // checks whether request is actual (was not deleted from requests queue); 
  // if actual, removes duplicate requests
  @Nullable
  private ScheduledData takeMeRemoveDuplicates(final Runnable key) {
    synchronized (myLock) {
      ScheduledData found = null;
      for (ScheduledData sentRequest : mySentRequests) {
        if (sentRequest.isMe(key)) {
          found = sentRequest;
          break;
        }
      }
      if (found != null) {
        // remove duplicates, left "me" - for cancel
        mySentRequests.retainAll(Collections.singletonList(found));
      }
      return found;
    }
  }

  private class MyRunnable implements Runnable {
    private final Consumer<Boolean> myDelegate;
    private final boolean myUpdateUnversionedFiles;

    private MyRunnable(final Consumer<Boolean> delegate, final boolean updateUnversionedFiles) {
      myDelegate = delegate;
      myUpdateUnversionedFiles = updateUnversionedFiles;
    }

    public void run() {
      final List<Runnable> copy = new ArrayList<Runnable>(myWaitingUpdateCompletionQueue.size());
      ScheduledData me = null;
      try {
        synchronized (myLock) {
          if (myUpdateUnversionedFiles) {
            updateUnversionedFilesEngaged = false;
          }
          else {
            updateWithoutUnversionedFilesEngaged = false;
          }

          if ((! myStopped) && ((! myStarted) || myPlVcsManager.isBackgroundVcsOperationRunning())) {
            // remove "me"
            mySentRequests.remove(me);
            // try again after time
            schedule(myUpdateUnversionedFiles);
            return;
          }
          me = takeMeRemoveDuplicates(this);
          if (myStopped || (me == null)) {
            return;
          }
          copy.addAll(myWaitingUpdateCompletionQueue);
        }
        myDelegate.consume(myUpdateUnversionedFiles);
      } finally {
        if (me != null) {
          synchronized (myLock) {
            // remove "me"
            mySentRequests.remove(me);
            if (! copy.isEmpty()) {
              myWaitingUpdateCompletionQueue.removeAll(copy);
            }
          }
          // do not run under lock
          for (Runnable runnable : copy) {
            runnable.run();
          }
        }
      }
    }
  }

  private static class ScheduledData {
    private final Runnable myKey;
    private final ScheduledFuture<?> myFuture;

    private ScheduledData(final Runnable key, final ScheduledFuture<?> future) {
      myKey = key;
      myFuture = future;
    }

    public boolean isMe(final Runnable key) {
      return key == myKey;
    }

    public void cancel() {
      myFuture.cancel(false);
    }
  }
}
