package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.concurrency.Semaphore;

public class SvnCopiesRefreshManager {
  private final SvnFileUrlMappingImpl myMapping;
  private DefendedCopiesRefreshProxy myCopiesRefreshProxy;

  public SvnCopiesRefreshManager(final Project project, final SvnVcs vcs) {
    myMapping = new SvnFileUrlMappingImpl(project, vcs);

    final MyVeryRefresh veryRefresh = new MyVeryRefresh();
    myCopiesRefreshProxy = new DefendedCopiesRefreshProxy(veryRefresh);

    final Runnable refresher = new MyRefresher(project, myMapping);
    final Runnable proxiedRefresher = myCopiesRefreshProxy.proxyRefresher(refresher);

    final RequestsMerger requestsMerger = new RequestsMerger(proxiedRefresher);
    veryRefresh.setRequestMerger(requestsMerger);

    // do one time after project loaded
    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
      public void run() {
        myCopiesRefreshProxy.asynchRequest();
      }
    });
  }

  public CopiesRefresh getCopiesRefresh() {
    return myCopiesRefreshProxy;
  }

  private class MyVeryRefresh implements CopiesRefresh {
    private static final long ourQueryInterval = 1000;
    private RequestsMerger myRequestMerger;
    private final ProgressManager myPm;

    private MyVeryRefresh() {
      myPm = ProgressManager.getInstance();
    }

    public void setRequestMerger(RequestsMerger requestMerger) {
      myRequestMerger = requestMerger;
    }

    public void ensureInit() {
      synchRequest(myPm.getProgressIndicator(), true);
    }

    public void asynchRequest() {
      myRequestMerger.request();
    }

    public void synchRequest() {
      synchRequest(myPm.getProgressIndicator(), false);
    }

    private void synchRequest(final ProgressIndicator pi, final boolean isOnlyInit) {
      final Semaphore semaphore = new Semaphore();
      final Runnable waiter = new Runnable() {
        public void run() {
          semaphore.up();
        }
      };
      if (isOnlyInit) {
        myRequestMerger.ensureInitialization(waiter);
      } else {
        myRequestMerger.waitRefresh(waiter);
      }
      semaphore.down();
      while (true) {
        if (semaphore.waitFor(ourQueryInterval)) break;
        if (pi != null) {
          pi.checkCanceled();
        }
      }
    }
  }

  public SvnFileUrlMappingImpl getMapping() {
    myCopiesRefreshProxy.ensureInit();
    return myMapping;
  }

  private static class MyRefresher implements Runnable {
    private final Project myProject;
    private final SvnFileUrlMappingImpl myMapping;

    private MyRefresher(final Project project, final SvnFileUrlMappingImpl mapping) {
      myProject = project;
      myMapping = mapping;
    }

    public void run() {
      myMapping.realRefresh();
      myProject.getMessageBus().syncPublisher(SvnVcs.ROOTS_RELOADED).run();
    }
  }
}
