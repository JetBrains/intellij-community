/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.ISVNSession;
import org.tmatesoft.svn.core.io.ISVNTunnelProvider;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/27/13
 * Time: 10:41 PM
 */
public class PrimitivePool implements ISVNRepositoryPool, ISVNSession {
  private final ISVNAuthenticationManager myManager;
  private final ISVNTunnelProvider myTunnelProvider;

  public PrimitivePool(ISVNAuthenticationManager manager, ISVNTunnelProvider tunnelProvider) {
    myManager = manager;
    myTunnelProvider = tunnelProvider;
  }

  @Override
  public SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException {
    final SVNRepository repos = SVNRepositoryFactory.create(url, this);
    repos.setAuthenticationManager(myManager);
    repos.setTunnelProvider(myTunnelProvider);
    repos.setDebugLog(new ProxySvnLog(SVNDebugLog.getDefaultLog()));
    repos.setCanceller(new MyCanceller());
    return repos;
  }

  @Override
  public void setAuthenticationManager(ISVNAuthenticationManager authManager) {
  }

  @Override
  public void setCanceller(ISVNCanceller canceller) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setDebugLog(ISVNDebugLog log) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void shutdownConnections(boolean shutdownAll) {
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean keepConnection(SVNRepository repository) {
    return false;
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

  private static class MyCanceller implements ISVNCanceller {
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
  }
}
