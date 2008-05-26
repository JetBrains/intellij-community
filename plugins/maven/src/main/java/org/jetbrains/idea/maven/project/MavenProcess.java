package org.jetbrains.idea.maven.project;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;

public class MavenProcess {
  private ProgressIndicator myIndicator;

  public MavenProcess(ProgressIndicator i) {
    myIndicator = i;
  }

  public ProgressIndicator getIndicator() {
    return myIndicator;
  }

  public void setText(String s) {
    if (myIndicator != null) myIndicator.setText(s);
  }

  public void setText2(String s) {
    if (myIndicator != null) myIndicator.setText2(s);
  }

  public void setFraction(double value) {
    if (myIndicator != null) myIndicator.setFraction(value);
  }

  public void checkCanceled() throws CanceledException {
    if (myIndicator != null && myIndicator.isCanceled()) throw new CanceledException();
  }

  public static void run(Project project, String title, MavenTask p) throws CanceledException {
    run(project, title, false, p);
  }

  public static MavenTaskHandler run(Project project,
                                     String title,
                                     boolean inBackground,
                                     final MavenTask t) throws CanceledException {
    if (inBackground) {
      final Semaphore startSemaphore = new Semaphore();
      final Semaphore finishSemaphore = new Semaphore();
      final ProgressIndicator[] indicator = new ProgressIndicator[1];

      startSemaphore.up();
      finishSemaphore.up();

      ProgressManager.getInstance().run(new Task.Backgroundable(project, title, true) {
        public void run(@NotNull ProgressIndicator i) {
          try {
            indicator[0] = i;
            startSemaphore.down();
            t.run(new MavenProcess(i));
          }
          catch (CanceledException ignore) {
          }
          finally {
            finishSemaphore.down();
          }
        }
      });

      return new MavenTaskHandler(startSemaphore, finishSemaphore, indicator);
    }
    else {
      final CanceledException[] canceledEx = new CanceledException[1];

      ProgressManager.getInstance().run(new Task.Modal(project, title, true) {
        public void run(@NotNull ProgressIndicator i) {
          try {
            t.run(new MavenProcess(i));
          }
          catch (CanceledException e) {
            canceledEx[0] = e;
          }
        }
      });
      if (canceledEx[0] != null) throw canceledEx[0];

      return null;
    }
  }

  public static class MavenTaskHandler {
    private Semaphore myStartSemaphore;
    private Semaphore myFinishSemaphore;
    private ProgressIndicator[] myIndicator;

    private MavenTaskHandler(Semaphore startSemaphore,
                             Semaphore finishSemaphore,
                             ProgressIndicator[] indicator) {
      myStartSemaphore = startSemaphore;
      myFinishSemaphore = finishSemaphore;
      myIndicator = indicator;
    }

    public void stopAndWaitForFinish() {
      myStartSemaphore.waitFor();
      myIndicator[0].cancel();
      myFinishSemaphore.waitFor();
    }

    public void waitFor() {
      myFinishSemaphore.waitFor();
    }
  }

  public static interface MavenTask {
    void run(MavenProcess p) throws CanceledException;
  }
}
