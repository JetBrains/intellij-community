package com.intellij.ide.startup.impl;

import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author mike
 */
public class StartupManagerImpl extends StartupManagerEx {
  private List<Runnable> myActivities = new ArrayList<Runnable>();
  private List<Runnable> myPostStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());
  private List<Runnable> myPreStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.impl.StartupManagerImpl");

  private FileSystemSynchronizer myFileSystemSynchronizer = new FileSystemSynchronizer();
  private boolean myStartupActivityRunning = false;
  private boolean myStartupActivityPassed = false;

  private Project myProject;

  public StartupManagerImpl(Project project) {
    myProject = project;
  }

  public void registerStartupActivity(Runnable runnable) {
    myActivities.add(runnable);
  }

  public synchronized void registerPostStartupActivity(Runnable runnable) {
    myPostStartupActivities.add(runnable);
  }

  public boolean startupActivityRunning() {
    return myStartupActivityRunning;
  }

  public boolean startupActivityPassed() {
    return myStartupActivityPassed;
  }

  public void registerPreStartupActivity(Runnable runnable) {
    myPreStartupActivities.add(runnable);
  }

  public FileSystemSynchronizer getFileSystemSynchronizer() {
    return myFileSystemSynchronizer;
  }

  public void runStartupActivities() {
    ApplicationManager.getApplication().runReadAction(
      new Runnable() {
        public void run() {
          runActivities(myPreStartupActivities);
          myFileSystemSynchronizer.setCancelable(true);
          myFileSystemSynchronizer.execute();
          myFileSystemSynchronizer = null;
          myStartupActivityRunning = true;
          runActivities(myActivities);

          myStartupActivityRunning = false;
          myStartupActivityPassed = true;
        }
      }
    );
  }

  public synchronized void runPostStartupActivities() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    runActivities(myPostStartupActivities);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      VirtualFileManager.getInstance().refresh(true);
    }
  }

  private static void runActivities(final List<Runnable> activities) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    try {

      while (!activities.isEmpty()) {
        final Runnable runnable = activities.remove(0);
        if (indicator != null) indicator.checkCanceled();

        try {
          runnable.run();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable ex) {
          LOG.error(ex);
        }
      }
    }
    finally {
      activities.clear();
    }
  }

  public void runWhenProjectIsInitialized(final Runnable action) {
    final Runnable runnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };

    if (myProject.isInitialized()) {
      runnable.run();
    }
    else {
      registerPostStartupActivity(new Runnable(){
        public void run() {
          runnable.run();
        }
      });
    }
  }
}
