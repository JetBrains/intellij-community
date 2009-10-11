/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.execution.SoutMavenConsole;
import org.jetbrains.idea.maven.utils.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MavenProjectsProcessor {
  private static final int WAIT_TIMEOUT = 2000;

  private final Project myProject;
  private final String myTitle;
  private final boolean myCancellable;
  private final MavenEmbeddersManager myEmbeddersManager;

  private final Thread myThread;
  private final BlockingQueue<MavenProjectsProcessorTask> myQueue = new LinkedBlockingQueue<MavenProjectsProcessorTask>();
  private volatile boolean isStopped;
  private volatile MavenUtil.MavenTaskHandler myCurrentTaskHandler;

  public MavenProjectsProcessor(Project project, String title, boolean cancellable, MavenEmbeddersManager embeddersManager) {
    myProject = project;
    myTitle = title;
    myCancellable = cancellable;
    myEmbeddersManager = embeddersManager;
    myThread = new Thread(new Runnable() {
      public void run() {
        try {
          while (doRunCycle()) { /* nothing */ }
        }
        catch (Throwable e) {
          MavenLog.LOG.error(e);
          throw new RuntimeException(e);
        }
      }
    }, getClass().getSimpleName() + ": " + title);

    if (isImmediateMode()) return;

    isStopped = false;
    myThread.start(); // rework if make inheritance
  }

  public void scheduleTask(MavenProjectsProcessorTask task) {
    synchronized (myQueue) {
      if (myQueue.contains(task)) return;
      myQueue.add(task);
      myQueue.notifyAll();
    }
  }

  public void removeTask(MavenProjectsProcessorTask task) {
    synchronized (myQueue) {
      myQueue.remove(task);
      myQueue.notifyAll();
    }
  }

  public void waitForCompletion() {
    if (isEmpty()) return;

    if (isImmediateMode()) {
      while (!isEmpty() && doRunCycle()) {/* do nothing */}
      return;
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    scheduleTask(new MavenProjectsProcessorTask() {
      public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
        throws MavenProcessCanceledException {
        semaphore.up();
      }
    });

    while (true) {
      if (isStopped || isEmpty() || semaphore.waitFor(WAIT_TIMEOUT)) return;
    }
  }

  private boolean isEmpty() {
    synchronized (myQueue) {
      return myQueue.isEmpty();
    }
  }

  private void cancelAllPendingRequests() {
    synchronized (myQueue) {
      myQueue.clear();
      myQueue.notifyAll();
    }
  }

  public void stop() {
    if (isImmediateMode()) return;

    isStopped = true;
    synchronized (myQueue) {
      myQueue.notifyAll();
    }
    cancelAllPendingRequests();

    MavenUtil.MavenTaskHandler handler = myCurrentTaskHandler;
    if (handler != null) handler.stop();

    //myThread.join();
  }

  public boolean doRunCycle() {
    try {
      synchronized (myQueue) {
        while (myQueue.isEmpty()) {
          myQueue.wait(WAIT_TIMEOUT);
          if (isStopped) return false;
        }
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    if (isStopped) return false;

    myCurrentTaskHandler = MavenUtil.runInBackground(myProject, myTitle, myCancellable, new MavenTask() {
      public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException {
        int counter = 0;
        while (true) {
          MavenProjectsProcessorTask task;
          int queueSize;

          synchronized (myQueue) {
            task = myQueue.peek();
            queueSize = myQueue.size();
          }
          if (isStopped || task == null) return;
          try {
            indicator.checkCanceled();

            counter++;
            indicator.setFraction(counter / (double)(counter + queueSize));

            String text = myTitle;
            if (queueSize > 0) text += " (" + (queueSize + 1) + " in queue)";
            indicator.setText(text);

            task.perform(myProject, myEmbeddersManager, new SoutMavenConsole(), indicator);
          }
          finally {
            synchronized (myQueue) {
              myQueue.remove(task); // remove the completed task from the queue
            }
          }
        }
      }
    });
    myCurrentTaskHandler.waitFor();
    if (myCurrentTaskHandler.isCancelled()) {
      cancelAllPendingRequests();
    }
    myCurrentTaskHandler = null;

    return !isStopped;
  }

  private boolean isImmediateMode() {
    return MavenUtil.isNoBackgroundMode();
  }
}
