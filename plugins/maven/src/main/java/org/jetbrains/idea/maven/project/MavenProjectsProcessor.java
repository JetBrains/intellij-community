package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.runner.SoutMavenConsole;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class MavenProjectsProcessor {
  private static final int QUEUE_POLL_INTERVAL = 100;

  private final Project myProject;
  private final MavenEmbeddersManager myEmbeddersManager;

  private final Thread myThread;
  private final BlockingQueue<MavenProjectsProcessorTask> myQueue = new LinkedBlockingQueue<MavenProjectsProcessorTask>();
  private volatile boolean isStopped;
  private final AtomicReference<MavenProgressIndicator> myCurrentProgressIndicator = new AtomicReference<MavenProgressIndicator>();

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

  public void scheduleTask(MavenProjectsProcessorTask task) {
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
      public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator process)
        throws MavenProcessCanceledException {
        semaphore.up();
      }

      public boolean immediateInTestMode() {
        return false;
      }
    });

    while (true) {
      if (isStopped || semaphore.waitFor(QUEUE_POLL_INTERVAL) || myQueue.isEmpty()) return;
    }
  }

  public void cancelNonBlocking() {
    myQueue.clear();
    MavenProgressIndicator indicator = myCurrentProgressIndicator.get();
    if (indicator != null) indicator.getIndicator().cancel();
  }

  public void cancelAndStop() {
    if (isUnitTestMode()) return;

    try {
      isStopped = true;
      cancelNonBlocking();
      myThread.join();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean doRunCycle() {
    MavenProjectsProcessorTask task;
    try {
      task = myQueue.poll(QUEUE_POLL_INTERVAL, TimeUnit.MILLISECONDS);
      fireQueueChanged(myQueue.size());
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    if (isStopped) return false;

    if (task != null) {
      try {
        doPerform(task);
      }
      catch (Throwable e) {
        MavenLog.LOG.error(e);
      }
    }
    return true;
  }

  private void doPerform(MavenProjectsProcessorTask task) {
    try {
      // todo console
      myCurrentProgressIndicator.set(new MavenProgressIndicator(new EmptyProgressIndicator()));
      task.perform(myProject, myEmbeddersManager, new SoutMavenConsole(), myCurrentProgressIndicator.get());
    }
    catch (MavenProcessCanceledException ignore) {
    }
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