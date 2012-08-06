/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.lowLevel;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableConvertor;
import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.*;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/30/12
 * Time: 2:17 PM
 */
public class SvnIdeaRepositoryPoolManager implements ISVNRepositoryPool, ISVNSession {
  private final static ApplicationLevelNumberConnectionsGuardImpl ourGuard;

  private final ISVNDebugLog myLog;
  private final ISVNCanceller myCanceller;

  private final SvnRepositoryPool myPool;
  private final boolean myKeepConnection;
  private final ISVNConnectionListener myListener;
  private ISVNAuthenticationManager myAuthManager;
  private ThrowableConvertor<SVNURL, SVNRepository, SVNException> myCreator;

  static {
    ourGuard = new ApplicationLevelNumberConnectionsGuardImpl();
  }

  public SvnIdeaRepositoryPoolManager(final boolean keepConnection, final ISVNAuthenticationManager authManager,
                                      final ISVNTunnelProvider tunnelProvider) {
    this(keepConnection, authManager, tunnelProvider, -1, -1);
  }

  public SvnIdeaRepositoryPoolManager(final boolean keepConnection,
                                      final ISVNAuthenticationManager authManager,
                                      final ISVNTunnelProvider tunnelProvider, final int maxCached, final int maxConcurrent) {
    myKeepConnection = keepConnection;
    myAuthManager = authManager;
    myLog = new ProxySvnLog(SVNDebugLog.getDefaultLog());
    myCanceller = createCanceller();

    final ThrowableConvertor<SVNURL, SVNRepository, SVNException> creator = new ThrowableConvertor<SVNURL, SVNRepository, SVNException>() {
      @Override
      public SVNRepository convert(SVNURL svnurl) throws SVNException {
        final SVNRepository repos = myCreator != null ? myCreator.convert(svnurl) : SVNRepositoryFactory.create(svnurl, SvnIdeaRepositoryPoolManager.this);
        repos.setAuthenticationManager(myAuthManager);
        repos.setTunnelProvider(tunnelProvider);
        repos.setDebugLog(myLog);
        repos.setCanceller(myCanceller);
        if (myKeepConnection) {
          repos.addConnectionListener(myListener);
        }
        return repos;
      }
    };

    final ThrowableConsumer<Pair<SVNURL, SVNRepository>, SVNException> adjuster = new ThrowableConsumer<Pair<SVNURL, SVNRepository>, SVNException>() {
      @Override
      public void consume(Pair<SVNURL, SVNRepository> pair) throws SVNException {
        SVNRepository repository = pair.getSecond();
        SVNURL url = pair.getFirst();
        repository.setLocation(url, false);
        repository.addConnectionListener(myListener);
        repository.setAuthenticationManager(myAuthManager);
        repository.setTunnelProvider(tunnelProvider);
        repository.setDebugLog(myLog);
        repository.setCanceller(myCanceller);
      }
    };

    if (keepConnection) {
      CachingSvnRepositoryPool pool = new CachingSvnRepositoryPool(creator, maxCached, maxConcurrent, adjuster, ourGuard);
      myPool = pool;
      myListener = new ISVNConnectionListener() {
        @Override
        public void connectionOpened(SVNRepository repository) {
          ourGuard.connectionOpened();
        }

        @Override
        public void connectionClosed(SVNRepository repository) {
          repository.removeConnectionListener(myListener);
          myPool.returnRepo(repository);
          ourGuard.connectionClosed();
        }
      };
      ourGuard.addRepositoryPool(pool);
    }
    else {
      myListener = null;
      myPool = new NoKeepConnectionPool(creator);
    }
  }

  public static ISVNCanceller createCanceller() {
    return new ISVNCanceller() {
      @Override
      public void checkCancelled() throws SVNCancelException {
        final ProgressManager pm = ProgressManager.getInstance();
        final ProgressIndicator pi = pm.getProgressIndicator();
        if (pi != null) {
          if (pi.isCanceled()) throw new SVNCancelException();
        }
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null && indicator.isCanceled()) {
          throw new SVNCancelException();
        }
      }
    };
  }

  @Override
  public void setAuthenticationManager(ISVNAuthenticationManager authManager) {
    myAuthManager = authManager;
  }

  @Override
  public void setCanceller(ISVNCanceller canceller) {
    // we didn't prepare for such use
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDebugLog(ISVNDebugLog log) {
    // we didn't prepare for such use
    throw new UnsupportedOperationException();
  }

  @Override
  public SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException {
    return myPool.getRepo(url, mayReuse);
  }

  @Override
  public void shutdownConnections(boolean shutdownAll) {
    dispose();
  }

  /**
   * Disposes this pool.
   *
   * @since 1.2.0
   */
  @Override
  public void dispose() {
    if (myKeepConnection) {
      ourGuard.removeRepositoryPool((CachingSvnRepositoryPool)myPool);
    }
    myPool.dispose();
  }

  @Override
  public boolean keepConnection(SVNRepository repository) {
    return myKeepConnection;
  }

  @Override
  public void saveCommitMessage(SVNRepository repository, long revision, String message) {
  }

  @Override
  public String getCommitMessage(SVNRepository repository, long revision) {
    return null;
  }

  @Override
  public boolean hasCommitMessage(SVNRepository repository, long revision) {
    return false;
  }

  public void setCreator(ThrowableConvertor<SVNURL, SVNRepository, SVNException> creator) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myCreator = creator;
  }

  public SvnRepositoryPool getPool() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return myPool;
  }

  public static ApplicationLevelNumberConnectionsGuardImpl getOurGuard() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return ourGuard;
  }
}
