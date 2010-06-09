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
package org.jetbrains.idea.maven.facade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.*;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MavenFacadeEmbedder extends Remote {
  void customizeForResolve(MavenFacadeConsole console, MavenFacadeProgressIndicator process) throws RemoteException;

  void customizeForResolve(Map<MavenId, File> projectIdToFileMap,
                           MavenFacadeConsole console,
                           MavenFacadeProgressIndicator process) throws RemoteException;

  void customizeForStrictResolve(Map<MavenId, File> projectIdToFileMap,
                                 MavenFacadeConsole console,
                                 MavenFacadeProgressIndicator process) throws RemoteException;

  @NotNull
  MavenWrapperExecutionResult resolveProject(@NotNull File file,
                                             @NotNull Collection<String> activeProfiles) throws RemoteException,
                                                                                                MavenFacadeProcessCanceledException;

  @NotNull
  MavenArtifact resolve(@NotNull MavenArtifactInfo info,
                        @NotNull List<MavenRemoteRepository> remoteRepositories) throws RemoteException,
                                                                                        MavenFacadeProcessCanceledException;

  @NotNull
  List<MavenArtifact> resolveTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                 @NotNull List<MavenRemoteRepository> remoteRepositories) throws RemoteException,
                                                                                                 MavenFacadeProcessCanceledException;

  Collection<MavenArtifact> resolvePlugin(@NotNull MavenPlugin plugin,
                                          @NotNull List<MavenRemoteRepository> repositories,
                                          int nativeMavenProjectId,
                                          boolean transitive) throws RemoteException, MavenFacadeProcessCanceledException;

  @NotNull
  MavenWrapperExecutionResult execute(@NotNull File file,
                                      @NotNull Collection<String> activeProfiles,
                                      @NotNull List<String> goals) throws RemoteException,
                                                                          MavenFacadeProcessCanceledException;

  void reset() throws RemoteException;

  void release() throws RemoteException;

  void clearCaches() throws RemoteException;

  void clearCachesFor(MavenId projectId) throws RemoteException;
}
