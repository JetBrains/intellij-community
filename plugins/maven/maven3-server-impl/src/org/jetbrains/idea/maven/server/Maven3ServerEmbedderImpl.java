/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;

import java.io.File;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

public class Maven3ServerEmbedderImpl extends MavenRemoteObject implements MavenServerEmbedder {
  public static Maven3ServerEmbedderImpl create(MavenServerSettings settings) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void customize(@Nullable MavenWorkspaceMap workspaceMap,
                        boolean failOnUnresolvedDependency,
                        @NotNull MavenServerConsole console,
                        @NotNull MavenServerProgressIndicator indicator) throws RemoteException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public MavenServerExecutionResult resolveProject(@NotNull File file, @NotNull Collection<String> activeProfiles)
    throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public MavenArtifact resolve(@NotNull MavenArtifactInfo info, @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<MavenArtifact> resolveTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                                 @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<MavenArtifact> resolvePlugin(@NotNull MavenPlugin plugin,
                                                 @NotNull List<MavenRemoteRepository> repositories,
                                                 int nativeMavenProjectId,
                                                 boolean transitive) throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public MavenServerExecutionResult execute(@NotNull File file, @NotNull Collection<String> activeProfiles, @NotNull List<String> goals)
    throws RemoteException, MavenServerProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reset() throws RemoteException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void release() throws RemoteException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearCaches() throws RemoteException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearCachesFor(MavenId projectId) throws RemoteException {
    throw new UnsupportedOperationException();
  }

  public static MavenModel interpolateAndAlignModel(MavenModel model, File basedir) {
    throw new UnsupportedOperationException();
  }

  public static MavenModel assembleInheritance(MavenModel model, MavenModel parentModel) {
    throw new UnsupportedOperationException();
  }

  public static ProfileApplicationResult applyProfiles(MavenModel model,
                                                       File basedir,
                                                       Collection<String> explicitProfiles,
                                                       Collection<String> alwaysOnProfiles) {
    throw new UnsupportedOperationException();
  }
}

