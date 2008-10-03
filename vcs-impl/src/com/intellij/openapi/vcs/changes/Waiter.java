package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;

import javax.swing.*;

public class Waiter implements Runnable {
  private final Project myProject;
  private final Runnable myRunnable;
  private boolean myDone;
  private final Object myLock = new Object();

  public Waiter(final Project project, final Runnable runnable) {
    myRunnable = runnable;
    myProject = project;
    myDone = false;
  }

  public void run() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setIndeterminate(true);
    indicator.setText2(VcsBundle.message("commit.wait.util.synched.text"));
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
