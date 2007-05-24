package com.intellij.openapi.progress.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiLock;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ProgressManagerImpl extends ProgressManager {
  @NonNls private static final String PROCESS_CANCELED_EXCEPTION = "idea.ProcessCanceledException";

  private final ThreadLocal<ProgressIndicator> myThreadIndicator = new ThreadLocal<ProgressIndicator>();
  private final AtomicInteger myCurrentProgressCount = new AtomicInteger(0);
  private final AtomicInteger myCurrentModalProgressCount = new AtomicInteger(0);

  private static volatile boolean ourNeedToCheckCancel = false;
  private static volatile int ourLockedCheckCounter = 0;
  private List<ProgressFunComponentProvider> myFunComponentProviders = new ArrayList<ProgressFunComponentProvider>();

  public ProgressManagerImpl(Application application) {
    if (!application.isUnitTestMode() && !Comparing.equal(System.getProperty(PROCESS_CANCELED_EXCEPTION), "disabled")) {
      new Thread("Progress Cancel Checker") {
        public void run() {
          while (true) {
            try {
              sleep(10);
            }
            catch (InterruptedException e) {
            }
            ourNeedToCheckCancel = true;
          }
        }
      }.start();
    }
  }

  public void checkCanceled() {
    // Q: how about 2 cancelable progresses in time??
    if (ourNeedToCheckCancel) { // smart optimization!
      ourNeedToCheckCancel = false;
      final ProgressIndicator progress = getProgressIndicator();
      if (progress != null) {
        try {
          progress.checkCanceled();
        }
        catch (ProcessCanceledException e) {
          if (!Thread.holdsLock(PsiLock.LOCK)) {
            ourLockedCheckCounter = 0;
            progress.checkCanceled();
          }
          else {
            ourLockedCheckCounter++;
            if (ourLockedCheckCounter > 10) {
              ourLockedCheckCounter = 0;
              ourNeedToCheckCancel = true;
            }
          }
        }
      }
    }
  }

  public JComponent getProvidedFunComponent(Project project, String processId) {
    for (ProgressFunComponentProvider provider : myFunComponentProviders) {
      JComponent cmp = provider.getProgressFunComponent(project, processId);
      if (cmp != null) return cmp;
    }
    return null;
  }

  public void setCancelButtonText(String cancelButtonText) {
    ProgressIndicator progressIndicator = getProgressIndicator();
    if (progressIndicator != null) {
      if (progressIndicator instanceof SmoothProgressAdapter && cancelButtonText != null) {
        ProgressIndicator original = ((SmoothProgressAdapter)progressIndicator).getOriginal();
        if (original instanceof ProgressWindow) {
          ((ProgressWindow)original).setCancelButtonText(cancelButtonText);
        }
      }
    }

  }

  public void registerFunComponentProvider(ProgressFunComponentProvider provider) {
    myFunComponentProviders.add(provider);
  }

  public void removeFunComponentProvider(ProgressFunComponentProvider provider) {
    myFunComponentProviders.remove(provider);
  }

  public boolean hasProgressIndicator() {
    return myCurrentProgressCount.get() > 0;
  }

  public boolean hasModalProgressIndicator() {
    return myCurrentModalProgressCount.get() > 0;
  }

  public void runProcess(@NotNull final Runnable process, final ProgressIndicator progress) throws ProcessCanceledException {
    executeProcessUnderProgress(new Runnable(){
      public void run() {
        synchronized (process) {
          process.notify();
        }
        try {
          if (progress != null && !progress.isRunning()) {
            progress.start();
          }
          process.run();
        }
        finally {
          if (progress != null && progress.isRunning()) {
            progress.stop();
          }
        }
      }
    },progress);
  }

  public void executeProcessUnderProgress(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    ProgressIndicator oldIndicator = myThreadIndicator.get();

    myThreadIndicator.set(progress);
    myCurrentProgressCount.incrementAndGet();

    final boolean modal = progress != null && progress.isModal();
    if (modal) myCurrentModalProgressCount.incrementAndGet();

    try {
      process.run();
    }
    finally {
      myThreadIndicator.set(oldIndicator);

      myCurrentProgressCount.decrementAndGet();
      if (modal) myCurrentModalProgressCount.decrementAndGet();
    }
  }

  public ProgressIndicator getProgressIndicator() {
    return myThreadIndicator.get();
  }

  public boolean runProcessWithProgressSynchronously(Runnable process, String progressTitle, boolean canBeCanceled, Project project) {
    return ((ApplicationEx)ApplicationManager.getApplication())
      .runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project);
  }

  private static boolean runProcessWithProgressSynchronously(final Task task) {
    final boolean result = ((ApplicationEx)ApplicationManager.getApplication())
      .runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          task.run(ProgressManager.getInstance().getProgressIndicator());
        }
      }, task.getTitle(), task.isCancellable(), task.getProject());
    if (result) {
      task.onSuccess();
    }
    else {
      task.onCancel();
    }
    return result;
  }

  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull String progressTitle,
                                                   @NotNull final Runnable process,
                                                   @Nullable final Runnable successRunnable,
                                                   @Nullable final Runnable canceledRunnable) {
    runProcessWithProgressAsynchronously(project, progressTitle, process, successRunnable, canceledRunnable, PerformInBackgroundOption.DEAF);
  }

  public void runProcessWithProgressAsynchronously(@NotNull final Project project,
                                                   @Nls @NotNull final String progressTitle,
                                                   @NotNull final Runnable process,
                                                   @Nullable final Runnable successRunnable,
                                                   @Nullable final Runnable canceledRunnable,
                                                   @NotNull final PerformInBackgroundOption option) {

    runProcessWithProgressAsynchronously(new Task.Backgroundable(project, progressTitle, true, option) {
      public void run(final ProgressIndicator indicator) {
        process.run();
      }


      public void onCancel() {
        if (canceledRunnable != null) {
          canceledRunnable.run();
        }
      }

      public void onSuccess() {
        if (successRunnable != null) {
          successRunnable.run();
        }
      }
    });
  }

  public static void runProcessWithProgressAsynchronously(final Task.Backgroundable task) {
    final BackgroundableProcessIndicator progressIndicator = new BackgroundableProcessIndicator(task);

    final Runnable process = new Runnable() {
      public void run() {
        task.run(progressIndicator);
      }
    };

    Runnable action = new Runnable() {
      public void run() {
        boolean canceled = false;
        try {
          ProgressManager.getInstance().runProcess(process, progressIndicator);
        }
        catch (ProcessCanceledException e) {
          canceled = true;
        }

        if (canceled) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              task.onCancel();
            }
          }, ModalityState.NON_MODAL);
        }
        else if (!canceled) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              task.onSuccess();
            }
          }, ModalityState.NON_MODAL);
        }
      }
    };

    synchronized (process) {
      ApplicationManager.getApplication().executeOnPooledThread(action);
      try {
        process.wait();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void run(@NotNull final Task task) {
    if (task.isHeadless()) {
      task.run(new EmptyProgressIndicator());
      return;
    }

    if (task.isModal()) {
      runProcessWithProgressSynchronously(task.asModal());
    } else {
      final Task.Backgroundable backgroundable = task.asBackgroundable();
      if (backgroundable.isConditionalModal() && !backgroundable.shouldStartInBackground()) {
        runProcessWithProgressSynchronously(task);
      }
      else {
        runProcessWithProgressAsynchronously(backgroundable);
      }
    }
  }
}