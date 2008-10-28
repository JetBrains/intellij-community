package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;

public class Waiter implements Runnable {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.Waiter");
  private final Project myProject;
  private final Runnable myRunnable;
  private boolean myStarted;
  private boolean myDone;
  private final Object myLock = new Object();

  public Waiter(final Project project, final Runnable runnable) {
    myRunnable = runnable;
    myProject = project;
    myDone = false;
    myStarted = false;
  }

  public void run() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setIndeterminate(true);
    indicator.setText2(VcsBundle.message("commit.wait.util.synched.text"));
    synchronized (myLock) {
      if (myStarted) {
        LOG.error("Waiter running under progress being started again.");
        return;
      }
      myStarted = true;
      while ((! myDone) && (! ProgressManager.getInstance().getProgressIndicator().isCanceled())) {
        try {
          myLock.wait();
        }
        catch (InterruptedException e) {
          // ok
        }
      }
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
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
    }, ModalityState.NON_MODAL);
  }

  public void done() {
    synchronized (myLock) {
      myDone = true;
      myLock.notifyAll();
    }
  }
}
