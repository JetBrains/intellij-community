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

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemStatUtilKt;
import com.intellij.openapi.externalSystem.statistics.ProjectImportCollector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.BTWMavenConsole;
import org.jetbrains.idea.maven.utils.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

public class MavenProjectsProcessor {
  private static final Logger LOG = Logger.getInstance(MavenProjectsProcessor.class);
  private final Project myProject;
  private final @NlsContexts.Command String myTitle;
  private final boolean myCancellable;
  private final MavenEmbeddersManager myEmbeddersManager;

  private final Queue<MavenProjectsProcessorTask> myQueue = new LinkedList<>();
  private boolean isProcessing;

  private volatile boolean isStopped;

  public MavenProjectsProcessor(Project project,
                                @NlsContexts.Command String title,
                                boolean cancellable,
                                MavenEmbeddersManager embeddersManager) {
    myProject = project;
    myTitle = title;
    myCancellable = cancellable;
    myEmbeddersManager = embeddersManager;
  }

  public void scheduleTask(MavenProjectsProcessorTask task) {
    synchronized (myQueue) {
      if (!isProcessing && !MavenUtil.isMavenUnitTestModeEnabled()) {
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

    if (MavenUtil.isMavenUnitTestModeEnabled()) {
      while (true) {
        MavenProjectsProcessorTask task;
        synchronized (myQueue) {
          task = myQueue.poll();
          if(task == null){
            return;
          }
        }
        startProcessing(task);
        }
      }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    scheduleTask(new MavenProjectsProcessorWaitForCompletionTask(semaphore));

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
      @Override
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

  private void doProcessPendingTasks(MavenProgressIndicator indicator,
                                     MavenProjectsProcessorTask task)
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

        MavenProjectsProcessorTask finalTask = task;
        StructuredIdeActivity activity = ExternalSystemStatUtilKt.importActivityStarted(myProject, MavenUtil.SYSTEM_ID, () ->
          Collections.singletonList(ProjectImportCollector.TASK_CLASS.with(finalTask.getClass()))
        );
        long startTime = System.currentTimeMillis();
        try {
          final MavenGeneralSettings mavenGeneralSettings = MavenProjectsManager.getInstance(myProject).getGeneralSettings();
          task.perform(myProject, myEmbeddersManager,
                       getMavenConsole(mavenGeneralSettings),
                       indicator);
        }
        catch (MavenProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          logImportErrorIfNotControlFlow(e);
        }
        finally {
          activity.finished();
          long duration = System.currentTimeMillis() - startTime;
          if (duration > 10) {
            LOG.info("[maven import] " + StringUtil.getShortName(task.getClass()) + " took " + duration + "ms");
          }
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

        while (!myQueue.isEmpty()) {
          MavenProjectsProcessorTask removedTask = myQueue.remove();
          if (removedTask instanceof MavenProjectsProcessorWaitForCompletionTask) {
            ((MavenProjectsProcessorWaitForCompletionTask)removedTask).mySemaphore.up();
          }
        }
        isProcessing = false;
      }
      throw e;
    }
  }

  @NotNull
  private MavenConsole getMavenConsole(MavenGeneralSettings mavenGeneralSettings) {
    return new BTWMavenConsole(myProject, mavenGeneralSettings.getOutputLevel(), mavenGeneralSettings.isPrintErrorStackTraces());
  }

  private void logImportErrorIfNotControlFlow(Throwable e) {
    if (e instanceof ControlFlowException) {
      ExceptionUtil.rethrowAllAsUnchecked(e);
    }
    ReadAction.run(() -> {
      if (myProject.isDisposed()) return;
      MavenLog.LOG.error(e);
      MavenProjectsManager.getInstance(myProject).showServerException(e);
    });
  }

  private static class MavenProjectsProcessorWaitForCompletionTask implements MavenProjectsProcessorTask {
    private final Semaphore mySemaphore;

    MavenProjectsProcessorWaitForCompletionTask(Semaphore semaphore) {mySemaphore = semaphore;}

    @Override
    public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
      throws MavenProcessCanceledException {
      mySemaphore.up();
    }
  }
}
