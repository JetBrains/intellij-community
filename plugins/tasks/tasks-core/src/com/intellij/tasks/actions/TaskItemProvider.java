package com.intellij.tasks.actions;

import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiManager;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.doc.TaskPsiElement;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.tasks.actions.TaskSearchSupport.getRepositoriesTasks;

/**
 * @author Mikhail Golubev
 */
class TaskItemProvider implements ChooseByNameItemProvider, Disposable {
  private static final Logger LOG = Logger.getInstance(TaskItemProvider.class);

  private static final int DELAY_PERIOD = 1000; // ms

  private final Project myProject;

  private int myCurrentOffset = 0;
  private boolean myOldEverywhere = false;
  private String myOldPattern = "";

  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  private final AtomicReference<Future<List<Task>>> myFutureReference = new AtomicReference<Future<List<Task>>>();

  public TaskItemProvider(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public List<String> filterNames(@NotNull ChooseByNameBase base, @NotNull String[] names, @NotNull String pattern) {
    return ContainerUtil.emptyList();
  }

  @Override
  public boolean filterElements(@NotNull ChooseByNameBase base,
                                @NotNull final String pattern,
                                final boolean everywhere,
                                @NotNull final ProgressIndicator cancelled,
                                @NotNull Processor<Object> consumer) {

    GotoTaskAction.CREATE_NEW_TASK_ACTION.setTaskName(pattern);
    if (!consumer.process(GotoTaskAction.CREATE_NEW_TASK_ACTION)) return false;

    List<Task> cachedAndLocalTasks = TaskSearchSupport.getLocalAndCachedTasks(TaskManager.getManager(myProject), pattern, everywhere);
    if (!processTasks(cachedAndLocalTasks, consumer, cancelled)) return false;

    if (cachedAndLocalTasks.size() >= base.getMaximumListSizeLimit()) {
      return true;
    }

    FutureTask<List<Task>> future = new FutureTask<List<Task>>(new Callable<List<Task>>() {
      @Override
      public List<Task> call() throws Exception {
          return fetchFromServer(pattern, everywhere, cancelled);
      }
    });

    // Newer request always wins
    Future<List<Task>> oldFeature = myFutureReference.getAndSet(future);
    if (oldFeature != null) {
      LOG.debug("Cancelling existing task");
      oldFeature.cancel(true);
    }

    if (!myAlarm.isDisposed()) {
      myAlarm.addRequest(future, DELAY_PERIOD);
      return false;
    }

    try {
      List<Task> tasks = future.get();
      myFutureReference.compareAndSet(future, null);
      tasks.removeAll(cachedAndLocalTasks);
      return processTasks(tasks, consumer, cancelled);
    }
    catch (InterruptedException interrupted) {
      Thread.interrupted();
    }
    catch (CancellationException e) {
      LOG.debug("Task cancelled");
    }
    catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ProcessCanceledException) {
        LOG.debug("Task cancelled via progress indicator");
      }
      else if (cause instanceof RuntimeException) {
        throw (RuntimeException)cause;
      }
      else if (cause instanceof Error) {
        throw (Error)cause;
      }
      else {
        throw new RuntimeException("Unknown checked exception", cause);
      }
    }
    return false;
  }

  /**
   * Download next page of tasks from server(s). If filtering settings changed, download them all over again, but always increment
   * upper limit to make progress. If server supports search on its side, it should not affect access time much, because only a few
   * tasks will be returned anyway.
   */
  private List<Task> fetchFromServer(String pattern, boolean everywhere, ProgressIndicator cancelled) {
    int offset, limit;
    // pattern changed -> reset pagination
    if (!myOldPattern.equals(pattern)) {
      myCurrentOffset = offset = 0;
      limit = GotoTaskAction.PAGE_SIZE;
    }
    // include closed tasks -> download all previous + closed, so no one is missing
    else if (myOldEverywhere != everywhere) {
      offset = 0;
      myCurrentOffset = limit = myCurrentOffset + GotoTaskAction.PAGE_SIZE;
    }
    // normal pagination step
    else {
      offset = myCurrentOffset;
      limit = GotoTaskAction.PAGE_SIZE;
      myCurrentOffset += GotoTaskAction.PAGE_SIZE;
    }
    List<Task> tasks = getRepositoriesTasks(TaskManager.getManager(myProject), pattern, offset, limit, true, everywhere, cancelled);
    myOldEverywhere = everywhere;
    myOldPattern = pattern;
    return tasks;
  }

  private boolean processTasks(List<Task> tasks, Processor<Object> consumer, ProgressIndicator cancelled) {
    if (!tasks.isEmpty() && !consumer.process(ChooseByNameBase.NON_PREFIX_SEPARATOR)) {
      return false;
    }
    PsiManager psiManager = PsiManager.getInstance(myProject);
    for (Task task : tasks) {
      cancelled.checkCanceled();
      if (!consumer.process(new TaskPsiElement(psiManager, task))) return false;
    }
    return true;
  }

  @Override
  public void dispose() {
    // Alarm should be disposed already
    Future<List<Task>> future = myFutureReference.get();
    if (future != null) {
      future.cancel(true);
    }
  }
}
