package com.intellij.ide.startup.impl;

import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * @author mike
 */
public class StartupManagerImpl extends StartupManagerEx implements ProjectComponent {
  private List<Runnable> myActivities = new ArrayList<Runnable>();
  private List<Runnable> myPostStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());
  private List<Runnable> myPreStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.impl.StartupManagerImpl");

  private FileSystemSynchronizer myFileSystemSynchronizer = new FileSystemSynchronizer();
  private boolean myStartupActivityRunning = false;
  private boolean myStartupActivityPassed = false;
  private boolean myPostStartupActivityPassed = false;

  private Project myProject;

  public StartupManagerImpl(Project project) {
    myProject = project;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  @NotNull
  public String getComponentName() {
    return "StartupManager";
  }

  public void registerStartupActivity(Runnable runnable) {
    myActivities.add(runnable);
  }

  public synchronized void registerPostStartupActivity(Runnable runnable) {
    myPostStartupActivities.add(runnable);
  }

  public synchronized void runPostStartup(Runnable runnable) {
    if (myPostStartupActivityPassed) {
      runnable.run();
    } else {
      registerPostStartupActivity(runnable);
    }
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
    myPostStartupActivityPassed = true;
  }

  private static void runActivities(final List<Runnable> activities) {
    try {

      while (!activities.isEmpty()) {
        final Runnable runnable = activities.remove(0);

        try {
          runnable.run();
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
