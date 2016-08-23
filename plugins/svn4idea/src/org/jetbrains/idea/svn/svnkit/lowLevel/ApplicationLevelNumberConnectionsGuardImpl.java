/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.svnkit.lowLevel;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.hash.HashSet;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/31/12
 * Time: 6:19 PM
 */
public class ApplicationLevelNumberConnectionsGuardImpl implements Disposable, ApplicationLevelNumberConnectionsGuard {
  public static final int DELAY = 20000;

  private final Object myLock;
  private final Set<CachingSvnRepositoryPool> mySet;
  private int myCurrentlyActiveConnections;
  private int myCurrentlyOpenedConnections;
  private boolean myDisposed;

  private int myInstanceCount;  // refreshable instances
  private final ScheduledExecutorService myService;
  private ScheduledFuture<?> myFuture;
  private final Runnable myRecheck;
  private int myDelay;
  private int myCurrentlyOpenedCount;

  public ApplicationLevelNumberConnectionsGuardImpl() {
    myDelay = DELAY;
    mySet = new HashSet<>();
    myService = Executors.newSingleThreadScheduledExecutor(ConcurrencyUtil.newNamedThreadFactory("SVN connection"));
    myLock = new Object();
    myDisposed = false;
    myRecheck = new Runnable() {
      @Override
      public void run() {
        HashSet<CachingSvnRepositoryPool> pools = new HashSet<>();
        synchronized (myLock) {
          pools.addAll(mySet);
        }
        for (CachingSvnRepositoryPool pool : pools) {
          pool.check();
        }
      }
    };
    myCurrentlyActiveConnections = 0;
    myCurrentlyOpenedConnections = 0;
  }

  public void setDelay(int delay) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myDelay = delay;
    myFuture.cancel(true);
    myFuture = myService.scheduleWithFixedDelay(myRecheck, myDelay, myDelay, TimeUnit.MILLISECONDS);
  }

  public int getCurrentlyActiveConnections() {
    synchronized (myLock) {
      assert ApplicationManager.getApplication().isUnitTestMode();
      return myCurrentlyActiveConnections;
    }
  }

  public int getInstanceCount() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return myInstanceCount;
  }

  public void addRepositoryPool(final CachingSvnRepositoryPool pool) {
    synchronized (myLock) {
      mySet.add(pool);
      ++ myInstanceCount;
      if (myFuture == null) {
        myFuture = myService.scheduleWithFixedDelay(myRecheck, myDelay, myDelay, TimeUnit.MILLISECONDS);
      }
    }
  }

  public void removeRepositoryPool(final CachingSvnRepositoryPool pool) {
    synchronized (myLock) {
      mySet.remove(pool);
      -- myInstanceCount;
      if (myInstanceCount == 0) {
        myFuture.cancel(true);
        myFuture = null;
      }
    }
  }

  @Override
  public void connectionCreated() {
    synchronized (myLock) {
      ++ myCurrentlyOpenedConnections;
    }
  }

  @Override
  public void connectionDestroyed(int number) {
    synchronized (myLock) {
      myCurrentlyOpenedConnections -= number;
    }
  }

  public void connectionOpened() {
    synchronized (myLock) {
      ++ myCurrentlyActiveConnections;
    }
  }

  public void connectionClosed() {
    synchronized (myLock) {
      -- myCurrentlyActiveConnections;
      myLock.notifyAll();
    }
  }

  @Override
  public void waitForTotalNumberOfConnectionsOk() throws SVNException {
    synchronized (myLock) {
      if (myCurrentlyActiveConnections >= CachingSvnRepositoryPool.ourMaxTotal) {
        waitForFreeConnections();
      }
    }
    // maybe too many opened? reduce request
    final Set<CachingSvnRepositoryPool> copy = new HashSet<>();
    synchronized (myLock) {
      if (myCurrentlyOpenedConnections >= CachingSvnRepositoryPool.ourMaxTotal) {
        copy.addAll(mySet);
      }
    }
    for (CachingSvnRepositoryPool pool : copy) {
      pool.closeInactive();
    }
    synchronized (myLock) {
      waitForFreeConnections();
    }
  }

  private void waitForFreeConnections() throws SVNException {
    synchronized (myLock) {
      while (myCurrentlyActiveConnections >= CachingSvnRepositoryPool.ourMaxTotal && ! myDisposed) {
        try {
          myLock.wait(500);
        }
        catch (InterruptedException e) {
          //
        }
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null && indicator.isCanceled()) {
          throw new SVNException(SVNErrorMessage.create(SVNErrorCode.CANCELLED));
        }
      }
    }
  }

  @Override
  public boolean shouldKeepConnectionLocally() {
    synchronized (myLock) {
      if (myCurrentlyActiveConnections > CachingSvnRepositoryPool.ourMaxTotal ||
          myCurrentlyOpenedConnections > CachingSvnRepositoryPool.ourMaxTotal) return false;
    }
    return true;
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      myDisposed = true;
      myLock.notifyAll();
    }
  }

  public int getCurrentlyOpenedCount() {
    synchronized (myLock) {
      return myCurrentlyOpenedCount;
    }
  }
}
