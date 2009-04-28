package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.runner.SoutMavenConsole;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MavenProjectsProcessor {
  private static final NullTask STOP_TASK = new NullTask();

  private final Project myProject;
  private final MavenEmbeddersManager myEmbeddersManager;

  private final Thread myThread;
  private final BlockingQueue<MavenProjectsProcessorTask> myQueue = new LinkedBlockingQueue<MavenProjectsProcessorTask>();
  private volatile boolean isStopped;

  private final Handler myHandler = new Handler();
  private final List<Listener> myListeners = ContainerUtil.createEmptyCOWList();

  public MavenProjectsProcessor(Project project, String title, MavenEmbeddersManager embeddersManager) {
    myProject = project;
    myEmbeddersManager = embeddersManager;
    myThread = new Thread(new Runnable() {
      public void run() {
        while (doRunCycle()) { /* nothing */ }
      }
    }, getClass().getSimpleName() + ": " + title);

    if (isUnitTestMode()) return;

    isStopped = false;
    myThread.start(); // rework if make inheritance
  }

  protected void scheduleTask(MavenProjectsProcessorTask task) {
    if (isUnitTestMode() && task.immediateInTestMode()) {
      try {
        doPerform(task);
      }
      catch (MavenProcessCanceledException e) {
        throw new RuntimeException(e);
      }
      return;
    }

    if (myQueue.contains(task)) return;
    myQueue.add(task);
    fireQueueChanged(myQueue.size());
  }

  public void removeTask(MavenProjectsProcessorTask task) {
    myQueue.remove(task);
  }

  public void waitForCompletion() {
    if (isUnitTestMode()) {
      while (!myQueue.isEmpty() && doRunCycle()) { /* do nothing */}
      return;
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    scheduleTask(new MavenProjectsProcessorTask() {
      public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProcess process)
        throws MavenProcessCanceledException {
        semaphore.up();
      }

      public boolean immediateInTestMode() {
        return false;
      }
    });
    while (true) {
      if (isStopped || semaphore.waitFor(100)) return;
    }
  }

  public void cancelNonBlocking() {
    myQueue.clear();
  }

  public void cancelAndStop() {
    if (isUnitTestMode()) return;

    try {
      isStopped = true;
      myQueue.put(STOP_TASK);
      myThread.join();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean doRunCycle() {
    MavenProjectsProcessorTask task;
    try {
      task = myQueue.poll(100, TimeUnit.MILLISECONDS);
      fireQueueChanged(myQueue.size());
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    if (isStopped) return false;
    if (task == null || task == STOP_TASK) return true;

    try {
      doPerform(task);
    }
    catch (MavenProcessCanceledException e) {
      return false;
    }
    catch (Throwable e) {
      MavenLog.LOG.error(e);
    }
    return true;
  }

  private void doPerform(final MavenProjectsProcessorTask task) throws MavenProcessCanceledException {
    // todo console and cancelation
    task.perform(myProject, myEmbeddersManager, new SoutMavenConsole(), new MavenProcess(new EmptyProgressIndicator()));
  }

  private void fireQueueChanged(int size) {
    for (Listener each : myListeners) {
      each.queueChanged(size);
    }
  }

  private boolean isUnitTestMode() {
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  public Handler getHandler() {
    return myHandler;
  }

  private static class NullTask implements MavenProjectsProcessorTask {
    public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProcess process)
      throws MavenProcessCanceledException {
      throw new UnsupportedOperationException();
    }

    public boolean immediateInTestMode() {
      throw new UnsupportedOperationException();
    }
  }

  public class Handler {
    public void addListener(Listener listener) {
      myListeners.add(listener);
    }

    public void cancelNonBlocking() {
      MavenProjectsProcessor.this.cancelNonBlocking();
    }
  }

  public interface Listener {
    void queueChanged(int size);
  }
}