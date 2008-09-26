package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

/**
 * for non-AWT threads, synchronously waits for completion of ChanegListManager update
 */
public class EnsureUpToDateFromNonAWTThread {
  private final Project myProject;
  private volatile boolean myDone;

  public EnsureUpToDateFromNonAWTThread(final Project project) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    assert ! ApplicationManager.getApplication().isDispatchThread();
    myProject = project;
    myDone = false;
  }

  public void execute() {
    assert ! ApplicationManager.getApplication().isDispatchThread();
    final Object lock = new Object();

    synchronized (lock) {
      ChangeListManager.getInstance(myProject).invokeAfterUpdate(new Runnable() {
        public void run() {
          synchronized (lock) {
            myDone = true;
            lock.notifyAll();
          }
        }
      }, false, true, null);

      while ((! myDone) && (! myProject.isDisposed())) {
        try {
          lock.wait();
        }
        catch (InterruptedException e) {
          //
        }
      }
    }
  }

  public boolean isDone() {
    return myDone;
  }
}
