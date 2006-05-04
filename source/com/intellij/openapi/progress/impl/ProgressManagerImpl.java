package com.intellij.openapi.progress.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressFunComponentProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiLock;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ProgressManagerImpl extends ProgressManager implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.ProgressManager");
  @NonNls private static final String PROCESS_CANCELED_EXCEPTION = "idea.ProcessCanceledException";

  private final HashMap<Thread, ProgressIndicator> myThreadToIndicatorMap = new HashMap<Thread, ProgressIndicator>();

  private static volatile boolean ourNeedToCheckCancel = false;
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
      final ProgressIndicator progress = getInstance().getProgressIndicator();
      if (progress != null) {
        try {
          // progress.checkCanceled();
        }
        catch (ProcessCanceledException e) {
          if (!Thread.holdsLock(PsiLock.LOCK)) {
            progress.checkCanceled();
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

  public String getComponentName() {
    return "ProgressManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public boolean hasProgressIndicator() {
    synchronized (myThreadToIndicatorMap) {
      return myThreadToIndicatorMap.size() != 0;
    }
  }

  public boolean hasModalProgressIndicator() {
    synchronized (myThreadToIndicatorMap) {
      for (ProgressIndicator indicator : myThreadToIndicatorMap.values()) {
        if (indicator.isModal()) {
          return true;
        }
      }
      return false;
    }
  }

  public void runProcess(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    Thread currentThread = Thread.currentThread();

    ProgressIndicator oldIndicator;
    synchronized (myThreadToIndicatorMap) {
      oldIndicator = myThreadToIndicatorMap.get(currentThread);
      if (progress != null) {
        myThreadToIndicatorMap.put(currentThread, progress);
      }
      else{
        myThreadToIndicatorMap.remove(currentThread);
      }
    }
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
      synchronized (myThreadToIndicatorMap) {
        if (oldIndicator != null) {
          myThreadToIndicatorMap.put(currentThread, oldIndicator);
        }
        else {
          myThreadToIndicatorMap.remove(currentThread);
        }
      }
    }
  }

  public ProgressIndicator getProgressIndicator() {
    synchronized (myThreadToIndicatorMap) {
      return myThreadToIndicatorMap.get(Thread.currentThread());
    }
  }

  public boolean runProcessWithProgressSynchronously(Runnable process, String progressTitle, boolean canBeCanceled, Project project) {
    return ((ApplicationEx)ApplicationManager.getApplication())
      .runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project);
  }
}