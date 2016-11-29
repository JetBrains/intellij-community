/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetBusyHandler;
import org.tmatesoft.sqljet.core.table.ISqlJetTransaction;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/19/12
 * Time: 12:09 PM
 */
// TODO: Such locking functionality is not required anymore. Likely to be removed (together with SvnProxies).
public abstract class SvnAbstractWriteOperationLocks {
  private final long myTimeout;
  private final static Map<String, Lock> myLockMap = new HashMap<>();
  private final static Object myLock = new Object();
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnAbstractWriteOperationLocks");
  private ISqlJetBusyHandler ourBusyHandler;
  private volatile boolean myDisposed;

  protected SvnAbstractWriteOperationLocks(long timeout) {
    myTimeout = timeout;
    ourBusyHandler = new ISqlJetBusyHandler() {
            @Override
            public boolean call(int i) {
              if (myDisposed) return false;
              TimeoutUtil.sleep(myTimeout);
              return true;
            }
          };
  }

  public void dispose() {
    myDisposed = true;
  }

  // null if not 1.7+ copy
  @Nullable
  private Lock getLockObject(File file) throws SVNException {
    final boolean directory = file.isDirectory();
    final WorkingCopy wcRoot = getCopy(file, directory);
    if (! wcRoot.is17Copy()) return null;
    Lock lock;
    final String path = FilePathsHelper.convertPath(wcRoot.getFile().getPath());
    synchronized (myLock) {
      lock = myLockMap.get(path);
      if (lock == null) {
        lock = new ReentrantLock();
        myLockMap.put(path, lock);
      }
    }
    return lock;
  }

  protected abstract WorkingCopy getCopy(File file, boolean directory) throws SVNException;

  public void lockWrite(final File file) throws SVNException {
    final Lock lock = getLockObject(file);
    if (lock != null) {
      lock.lock();
    }
  }

  public void unlockWrite(final File file) throws SVNException {
    final Lock lock = getLockObject(file);
    if (lock != null) {
      lock.unlock();
    }
  }

  // would wait until read is available
  public void wrapRead(final File file, final Runnable runnable) throws SVNException {
    final WorkingCopy copy = getCopy(file, file.isDirectory());
    if (! copy.is17Copy()) {
      runnable.run();
      return;
    }

    final File root = copy.getFile();
    SqlJetDb open = null;
    final boolean run[] = new boolean[1];
    run[0] = false;
    try {
      open = SqlJetDb.open(SvnUtil.getWcDb(root), false);
      open.setBusyHandler(ourBusyHandler);
      try {
        final SqlJetDb finalOpen = open;
        open.runReadTransaction(new ISqlJetTransaction() {
          @Override
          public Object run(SqlJetDb db) throws SqlJetException {
            run[0] = true;
            runnable.run();
            return null;
          }
        });
      } finally {
        open.rollback();
      }
    }
    catch (SqlJetException e) {
      LOG.info(e);
      if (! run[0]) {
        runnable.run();
      }
    }
    finally {
      if (open != null) {
        try {
          open.close();
        }
        catch (SqlJetException e) {
          LOG.info(e);
        }
      }
    }
  }
}
