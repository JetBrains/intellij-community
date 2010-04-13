/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.lifecycle.AtomicSectionsAware;
import com.intellij.lifecycle.ControlledAlarmFactory;
import com.intellij.lifecycle.SlowlyClosingAlarm;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.RequestsMerger;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;

public class SvnCopiesRefreshManager {
  private final CopiesRefresh myCopiesRefresh;

  public SvnCopiesRefreshManager(final Project project, final SvnFileUrlMappingImpl mapping) {
    myCopiesRefresh = new MyVeryRefresh();
    //myCopiesRefreshProxy = new DefendedCopiesRefreshProxy(veryRefresh);

    final SlowlyClosingAlarm alarm = ControlledAlarmFactory.createOnOwnThread(project, "Subversion working copies refresher");
    final Runnable refresher = new MyRefresher(project, mapping, alarm);
    //final Runnable proxiedRefresher = myCopiesRefreshProxy.proxyRefresher(refresher);

    final RequestsMerger requestsMerger = new RequestsMerger(refresher, new Consumer<Runnable>() {
      public void consume(final Runnable runnable) {
        alarm.addRequest(runnable);
      }
    });
    ((MyVeryRefresh) myCopiesRefresh).setRequestMerger(requestsMerger);
  }

  public CopiesRefresh getCopiesRefresh() {
    return myCopiesRefresh;
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
      semaphore.down();
      if (isOnlyInit) {
        myRequestMerger.ensureInitialization(waiter);
      } else {
        myRequestMerger.waitRefresh(waiter);
      }
      while (true) {
        if (semaphore.waitFor(ourQueryInterval)) break;
        if (pi != null) {
          pi.checkCanceled();
        }
      }
    }
  }

  private static class MyRefresher implements Runnable {
    private final Project myProject;
    private final SvnFileUrlMappingImpl myMapping;
    private final AtomicSectionsAware myAtomicSectionsAware;

    private MyRefresher(final Project project, final SvnFileUrlMappingImpl mapping, final AtomicSectionsAware atomicSectionsAware) {
      myProject = project;
      myMapping = mapping;
      myAtomicSectionsAware = atomicSectionsAware;
    }

    public void run() {
      try {
        myMapping.realRefresh(myAtomicSectionsAware);
      }
      catch (ProcessCanceledException e) {
        //
      }
    }
  }
}
