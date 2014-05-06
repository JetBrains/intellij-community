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
package org.jetbrains.idea.svn.svnkit;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.lowLevel.PrimitivePool;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitManager {

  @NotNull private final SvnVcs myVcs;
  @NotNull private final Project myProject;
  @NotNull private final SvnConfiguration myConfiguration;

  public SvnKitManager(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myProject = myVcs.getProject();
    myConfiguration = myVcs.getSvnConfiguration();
  }

  public ISVNOptions getSvnOptions() {
    return myConfiguration.getOptions(myProject);
  }

  public SVNRepository createRepository(String url) throws SVNException {
    SVNRepository repos = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
    repos.setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    repos.setTunnelProvider(myConfiguration.getOptions(myProject));
    return repos;
  }

  public SVNRepository createRepository(SVNURL url) throws SVNException {
    SVNRepository repos = SVNRepositoryFactory.create(url);
    repos.setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    repos.setTunnelProvider(myConfiguration.getOptions(myProject));
    return repos;
  }

  @NotNull
  private ISVNRepositoryPool getPool() {
    return getPool(myConfiguration.getAuthenticationManager(myVcs));
  }

  @NotNull
  private ISVNRepositoryPool getPool(ISVNAuthenticationManager manager) {
    if (myProject.isDisposed()) {
      throw new ProcessCanceledException();
    }
    return new PrimitivePool(manager, myConfiguration.getOptions(myProject));
  }

  public SVNUpdateClient createUpdateClient() {
    final SVNUpdateClient client = new SVNUpdateClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    return client;
  }

  public SVNUpdateClient createUpdateClient(@NotNull ISVNAuthenticationManager manager) {
    final SVNUpdateClient client = new SVNUpdateClient(getPool(manager), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(manager);
    return client;
  }

  public SVNStatusClient createStatusClient() {
    SVNStatusClient client = new SVNStatusClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    client.setIgnoreExternals(false);
    return client;
  }

  public SVNWCClient createWCClient() {
    final SVNWCClient client = new SVNWCClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    return client;
  }

  public SVNWCClient createWCClient(@NotNull ISVNAuthenticationManager manager) {
    final SVNWCClient client = new SVNWCClient(getPool(manager), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(manager);
    return client;
  }

  public SVNCopyClient createCopyClient() {
    final SVNCopyClient client = new SVNCopyClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    return client;
  }

  public SVNMoveClient createMoveClient() {
    final SVNMoveClient client = new SVNMoveClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    return client;
  }

  public SVNLogClient createLogClient() {
    final SVNLogClient client = new SVNLogClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    return client;
  }

  public SVNLogClient createLogClient(@NotNull ISVNAuthenticationManager manager) {
    final SVNLogClient client = new SVNLogClient(getPool(manager), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(manager);
    return client;
  }

  public SVNCommitClient createCommitClient() {
    final SVNCommitClient client = new SVNCommitClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    return client;
  }

  public SVNDiffClient createDiffClient() {
    final SVNDiffClient client = new SVNDiffClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    return client;
  }

  public SVNChangelistClient createChangelistClient() {
    final SVNChangelistClient client = new SVNChangelistClient(getPool(), myConfiguration.getOptions(myProject));
    client.getOperationsFactory().setAuthenticationManager(myConfiguration.getAuthenticationManager(myVcs));
    return client;
  }
}
