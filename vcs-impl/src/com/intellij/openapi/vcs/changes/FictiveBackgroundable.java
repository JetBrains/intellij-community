package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class FictiveBackgroundable extends Task.Backgroundable {
  private final Runnable myRunnable;
  private boolean myDone;
  private final Object myLock = new Object();

  FictiveBackgroundable(@Nullable final Project project, @NotNull final Runnable runnable, final boolean cancellable, final String title) {
    super(project, VcsBundle.message("change.list.manager.wait.lists.synchronization", title), cancellable, new PerformInBackgroundOption() {
      public boolean shouldStartInBackground() {
        return true;
      }
      public void processSentToBackground() {
      }

      public void processRestoredToForeground() {
      }
    });
    myRunnable = runnable;
    myDone = false;
  }

  public void run(final ProgressIndicator indicator) {
    synchronized (myLock) {
      while ((! myDone) && (! ProgressManager.getInstance().getProgressIndicator().isCanceled())) {
        try {
          myLock.wait();
        }
        catch (InterruptedException e) {
          // ok
        }
      }
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        synchronized (myLock) {
          if (! myDone) {
            return;
          }
        }
        if (myProject.isDisposed()) return;
        myRunnable.run();
        ChangesViewManager.getInstance(myProject).refreshView();
      }
    });
  }

  public void done() {
    synchronized (myLock) {
      myDone = true;
      myLock.notifyAll();
    }
  }
}
