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
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;
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

  @NotNull
  public ISVNOptions getSvnOptions() {
    return myConfiguration.getOptions(myProject);
  }

  @NotNull
  public SVNRepository createRepository(String url) throws SVNException {
    return createRepository(SVNURL.parseURIEncoded(url));
  }

  @NotNull
  public SVNRepository createRepository(@NotNull SVNURL url) throws SVNException {
    SVNRepository repository = SVNRepositoryFactory.create(url);
    repository.setAuthenticationManager(getAuthenticationManager());
    repository.setTunnelProvider(getSvnOptions());

    return repository;
  }

  @NotNull
  private ISVNRepositoryPool getPool() {
    return getPool(getAuthenticationManager());
  }

  @NotNull
  private ISVNRepositoryPool getPool(@NotNull ISVNAuthenticationManager manager) {
    if (myProject.isDisposed()) {
      throw new ProcessCanceledException();
    }
    return new PrimitivePool(manager, getSvnOptions());
  }

  @NotNull
  public SVNUpdateClient createUpdateClient() {
    return setupClient(new SVNUpdateClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNUpdateClient createUpdateClient(@NotNull ISVNAuthenticationManager manager) {
    return setupClient(new SVNUpdateClient(getPool(manager), getSvnOptions()), manager);
  }

  @NotNull
  public SVNStatusClient createStatusClient() {
    SVNStatusClient client = new SVNStatusClient(getPool(), getSvnOptions());
    client.setIgnoreExternals(false);

    return setupClient(client);
  }

  @NotNull
  public SVNWCClient createWCClient() {
    return setupClient(new SVNWCClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNWCClient createWCClient(@NotNull ISVNAuthenticationManager manager) {
    return setupClient(new SVNWCClient(getPool(manager), getSvnOptions()), manager);
  }

  @NotNull
  public SVNCopyClient createCopyClient() {
    return setupClient(new SVNCopyClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNMoveClient createMoveClient() {
    return setupClient(new SVNMoveClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNLogClient createLogClient() {
    return setupClient(new SVNLogClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNLogClient createLogClient(@NotNull ISVNAuthenticationManager manager) {
    return setupClient(new SVNLogClient(getPool(manager), getSvnOptions()), manager);
  }

  @NotNull
  public SVNCommitClient createCommitClient() {
    return setupClient(new SVNCommitClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNDiffClient createDiffClient() {
    return setupClient(new SVNDiffClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNChangelistClient createChangelistClient() {
    return setupClient(new SVNChangelistClient(getPool(), getSvnOptions()));
  }

  @NotNull
  private SvnAuthenticationManager getAuthenticationManager() {
    return myConfiguration.getAuthenticationManager(myVcs);
  }

  @NotNull
  private <T extends SVNBasicClient> T setupClient(@NotNull T client) {
    return setupClient(client, getAuthenticationManager());
  }

  @NotNull
  private static <T extends SVNBasicClient> T setupClient(@NotNull T client, @NotNull ISVNAuthenticationManager manager) {
    client.getOperationsFactory().setAuthenticationManager(manager);

    return client;
  }
}
