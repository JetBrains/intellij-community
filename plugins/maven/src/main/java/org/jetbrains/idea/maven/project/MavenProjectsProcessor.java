package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.runner.SoutMavenConsole;
import org.jetbrains.idea.maven.utils.*;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MavenProjectsProcessor {
  private static final int WAIT_TIMEOUT = 2000;

  private final Project myProject;
  private final String myTitle;
  private final boolean mySystem;
  private final MavenEmbeddersManager myEmbeddersManager;

  private final Thread myThread;
  private final BlockingQueue<MavenProjectsProcessorTask> myQueue = new LinkedBlockingQueue<MavenProjectsProcessorTask>();
  private volatile int myQueueSize;
  private volatile boolean isStopped;
  private volatile MavenUtil.MavenTaskHandler myCurrentTaskHandler;

  private final Handler myHandler = new Handler();
  private final List<Listener> myListeners = ContainerUtil.createEmptyCOWList();

  public MavenProjectsProcessor(Project project, String title, boolean isSystem, MavenEmbeddersManager embeddersManager) {
    myProject = project;
    myTitle = title;
    mySystem = isSystem;
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
    synchronized (myQueue) {
      if (myQueue.contains(task)) return;
      myQueue.add(task);
      myQueueSize = myQueue.size();
      myQueue.notifyAll();
    }
    fireQueueChanged(myQueueSize);
  }

  public void removeTask(MavenProjectsProcessorTask task) {
    synchronized (myQueue) {
      myQueue.remove(task);
      myQueue.notifyAll();
    }
  }

  public void waitForCompletion() {
    if (isEmpty()) return;

    if (isUnitTestMode()) {
      while (!isEmpty() && doRunCycle()) {/* do nothing */}
      return;
    }

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    scheduleTask(new MavenProjectsProcessorTask() {
      public void perform(Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator process)
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

  public void cancelNonBlocking() {
    synchronized (myQueue) {
      myQueue.clear();
      myQueue.notifyAll();
      myQueueSize = 0;
    }
    fireQueueChanged(myQueueSize);

    MavenUtil.MavenTaskHandler handler = myCurrentTaskHandler;
    if (handler != null) handler.stop();
  }

  public void cancelAndStop() {
    if (isUnitTestMode()) return;

    try {
      isStopped = true;
      synchronized (myQueue) {
        myQueue.notifyAll();
      }
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
      synchronized (myQueue) {
        while (myQueue.isEmpty()) {
          myQueue.wait(WAIT_TIMEOUT);
          if (isStopped) return false;
        }
        task = myQueue.poll();
        myQueueSize = myQueue.size();
      }
      fireQueueChanged(myQueueSize);
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

  private void doPerform(final MavenProjectsProcessorTask task) {
    if (mySystem) {
      try {
        task.perform(myProject, myEmbeddersManager, new SoutMavenConsole(), new MavenProgressIndicator(new EmptyProgressIndicator()));
      }
      catch (MavenProcessCanceledException ignore) {
      }
    }
    else {
      // todo console
      MavenTask mavenTask = new MavenTask() {
        public void run(MavenProgressIndicator process) throws MavenProcessCanceledException {
          task.perform(myProject, myEmbeddersManager, new SoutMavenConsole(), process);
        }
      };
      String title = myTitle;
      if (myQueueSize > 0) title += " (" + myQueueSize + " in queue)";
      myCurrentTaskHandler = MavenUtil.runInBackground(myProject, title, mavenTask);
      myCurrentTaskHandler.waitFor();
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
