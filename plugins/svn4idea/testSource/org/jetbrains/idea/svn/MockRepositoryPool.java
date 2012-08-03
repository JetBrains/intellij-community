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
package org.jetbrains.idea.svn;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CancelHelper;
import com.intellij.util.ThrowableConvertor;
import org.jetbrains.idea.svn.lowLevel.ApplicationLevelNumberConnectionsGuardImpl;
import org.jetbrains.idea.svn.lowLevel.ProxySvnLog;
import org.jetbrains.idea.svn.lowLevel.SvnIdeaRepositoryPoolManager;
import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.ISVNConnectionListener;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/3/12
 * Time: 5:23 PM
 */
public class MockRepositoryPool implements ISVNRepositoryPool {
  private final ApplicationLevelNumberConnectionsGuardImpl myGuard;
  private final Project myProject;
  private final CancelHelper myCancelHelper;
  private final ProxySvnLog mySvnLog;
  private final ISVNCanceller myCanceller;
  private final ISVNConnectionListener myListener;
 // private final CachingSvnRepositoryPool myPool;

  public MockRepositoryPool(final Project project) {
    myProject = project;
    myGuard = new ApplicationLevelNumberConnectionsGuardImpl();
    myCancelHelper = CancelHelper.getInstance(project);
    mySvnLog = new ProxySvnLog(SVNDebugLog.getDefaultLog(), myCancelHelper);
    myCanceller = SvnIdeaRepositoryPoolManager.createCanceller(myCancelHelper);

    myListener = new ISVNConnectionListener() {
      @Override
      public void connectionOpened(SVNRepository repository) {
        myGuard.connectionOpened();
      }

      @Override
      public void connectionClosed(SVNRepository repository) {
        repository.removeConnectionListener(myListener);
        //myPool.returnRepo(repository);
        myGuard.connectionClosed();
      }
    };

    final ThrowableConvertor<SVNURL, SVNRepository, SVNException> creator = new ThrowableConvertor<SVNURL, SVNRepository, SVNException>() {
      @Override
      public SVNRepository convert(SVNURL svnurl) throws SVNException {
        MockSvnRepository repository = new MockSvnRepository(svnurl, ISVNSession.DEFAULT);
        repository.setDebugLog(mySvnLog);
        repository.setCanceller(myCanceller);
        repository.addConnectionListener(myListener);
        return repository;
      }
    };

   // myPool = new CachingSvnRepositoryPool(creator, -1, -1, adjuster, myProgressProxy, ourGuard);
  }

  @Override
  public void setAuthenticationManager(ISVNAuthenticationManager authManager) {
  }

  @Override
  public void setCanceller(ISVNCanceller canceller) {
    // todo
  }

  @Override
  public void setDebugLog(ISVNDebugLog log) {
  }

  @Override
  public SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException {
    return null;
  }

  @Override
  public void shutdownConnections(boolean shutdownAll) {
    dispose();
  }

  @Override
  public void dispose() {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
