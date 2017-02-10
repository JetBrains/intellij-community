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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.idea.maven.execution.SoutMavenConsole;
import org.jetbrains.idea.maven.utils.*;

import java.util.LinkedList;
import java.util.Queue;

public class MavenProjectsProcessor {
  private final Project myProject;
  private final String myTitle;
  private final boolean myCancellable;
  private final MavenEmbeddersManager myEmbeddersManager;

  private final Queue<MavenProjectsProcessorTask> myQueue = new LinkedList<>();
  private boolean isProcessing;

  private volatile boolean isStopped;

  public MavenProjectsProcessor(Project project, String title, boolean cancellable, MavenEmbeddersManager embeddersManager) {
    myProject = project;
    myTitle = title;
    myCancellable = cancellable;
    myEmbeddersManager = embeddersManager;
  }

  public void scheduleTask(MavenProjectsProcessorTask task) {
    synchronized (myQueue) {
      if (!isProcessing && !ApplicationManager.getApplication().isUnitTestMode()) {
        isProcessing = true;
        startProcessing(task);
        return;
      }
      if (myQueue.contains(task)) return;
      myQueue.add(task);
    }
  }

  public void removeTask(MavenProjectsProcessorTask task) {
    synchronized (myQueue) {
      myQueue.remove(task);
    }
  }

  public void waitForCompletion() {
    if (isStopped) return;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      synchronized (myQueue) {
        while (!myQueue.isEmpty()) {
          startProcessing(myQueue.poll());
        }
      }
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
      if (isStopped || semaphore.waitFor(1000)) return;
    }
  }

  public void stop() {
    isStopped = true;
    synchronized (myQueue) {
      myQueue.clear();
    }
  }

  private void startProcessing(final MavenProjectsProcessorTask task) {
    MavenUtil.runInBackground(myProject, myTitle, myCancellable, new MavenTask() {
      public void run(MavenProgressIndicator indicator) throws MavenProcessCanceledException {
        Condition<MavenProgressIndicator> condition = mavenProgressIndicator -> isStopped;
        indicator.addCancelCondition(condition);
        try {
          doProcessPendingTasks(indicator, task);
        }
        finally {
          indicator.removeCancelCondition(condition);
        }
      }
    });
  }

  private void doProcessPendingTasks(MavenProgressIndicator indicator, MavenProjectsProcessorTask task)
    throws MavenProcessCanceledException {
    int counter = 0;
    try {
      while (true) {
        indicator.checkCanceled();
        counter++;

        int remained;
        synchronized (myQueue) {
          remained = myQueue.size();
        }
        indicator.setFraction(counter / (double)(counter + remained));

        try {
          final MavenGeneralSettings mavenGeneralSettings = MavenProjectsManager.getInstance(myProject).getGeneralSettings();
          task.perform(myProject, myEmbeddersManager,
                       new SoutMavenConsole(mavenGeneralSettings.getOutputLevel(), mavenGeneralSettings.isPrintErrorStackTraces()),
                       indicator);
        }
        catch (MavenProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          MavenLog.LOG.error(e);
          new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP,
                           "Unable to import maven project",
                           "See logs for details",
                           NotificationType.ERROR
                           ).notify(myProject);
        }

        synchronized (myQueue) {
          task = myQueue.poll();
          if (task == null) {
            isProcessing = false;
            return;
          }
        }
      }
    }
    catch (MavenProcessCanceledException e) {
      synchronized (myQueue) {
        myQueue.clear();
        isProcessing = false;
      }
      throw e;
    }
  }
}
