package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.embedder.MavenConsoleImpl;
import org.jetbrains.idea.maven.runner.SoutMavenConsole;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class MavenProjectsProcessor {
  private static final NullTask NULL_TASK = new NullTask();

  private final Project myProject;
  private final String myTitle;
  private final MavenEmbeddersManager myEmbeddersManager;
  private final MavenConsole myConsole;
  private final Thread myThread;
  private final BlockingQueue<MavenProjectsProcessorTask> myQueue = new LinkedBlockingQueue<MavenProjectsProcessorTask>();
  private volatile boolean isStopped;

  public MavenProjectsProcessor(Project project, String title, MavenEmbeddersManager embeddersManager) {
    myProject = project;
    myTitle = title;
    myEmbeddersManager = embeddersManager;
    myConsole = isUnitTestMode() ? new SoutMavenConsole() : new MavenConsoleImpl(title, myProject);
    myThread = new Thread(new Runnable() {
      public void run() {
        while (doRunCycle()) { /* nothing */ }
      }
    }, getClass().getSimpleName());

    if (isUnitTestMode()) return;

    isStopped = false;
    myThread.start(); // rework if make inheritance
  }

  protected void scheduleTask(MavenProjectsProcessorTask task) {
    if (task.immediateInTestMode() && isUnitTestMode()) {
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

  public void cancelAndStop() {
    if (isUnitTestMode()) return;

    try {
      isStopped = true;
      myQueue.put(NULL_TASK);
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
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    if (isStopped) return false;
    if (task == null || task == NULL_TASK) return true;

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
    task.perform(myProject, myEmbeddersManager, myConsole, new MavenProcess(new EmptyProgressIndicator()));
    //MavenProcess.runInBackground(myProject, myTitle, false, new MavenProcess.MavenTask() {
    //  @Override
    //  public void run(MavenProcess process) throws MavenProcessCanceledException {
    //    task.perform(myProject, myEmbeddersManager, myConsole, process);
    //  }
    //}).waitFor();
  }

  private boolean isUnitTestMode() {
    return ApplicationManager.getApplication().isUnitTestMode();
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
}