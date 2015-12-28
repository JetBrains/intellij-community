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
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

public interface MavenServerEmbedder extends Remote {
  String MAVEN_EMBEDDER_VERSION = "idea.maven.embedder.version";
  String MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS = "idea.maven.embedder.ext.cli.args";

  void customize(@Nullable MavenWorkspaceMap workspaceMap,
                 boolean failOnUnresolvedDependency,
                 @NotNull MavenServerConsole console,
                 @NotNull MavenServerProgressIndicator indicator,
                 boolean alwaysUpdateSnapshots) throws RemoteException;

  void customizeComponents() throws RemoteException;

  @NotNull
  List<String> retrieveAvailableVersions(@NotNull String groupId,
                                         @NotNull String artifactId,
                                         @NotNull List<MavenRemoteRepository> remoteRepositories)
    throws RemoteException;


  @NotNull
  Collection<MavenServerExecutionResult> resolveProject(@NotNull Collection<File> files,
                                                        @NotNull Collection<String> activeProfiles,
                                                        @NotNull Collection<String> inactiveProfiles) throws RemoteException,
                                                                                                 MavenServerProcessCanceledException;

  @Nullable
  String evaluateEffectivePom(@NotNull File file,
                              @NotNull List<String> activeProfiles,
                              @NotNull List<String> inactiveProfiles) throws RemoteException,
                                                                             MavenServerProcessCanceledException;

  @NotNull
  MavenArtifact resolve(@NotNull MavenArtifactInfo info,
                        @NotNull List<MavenRemoteRepository> remoteRepositories) throws RemoteException,
                                                                                        MavenServerProcessCanceledException;

  @NotNull
  List<MavenArtifact> resolveTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                          @NotNull List<MavenRemoteRepository> remoteRepositories) throws RemoteException,
                                                                                                          MavenServerProcessCanceledException;

  Collection<MavenArtifact> resolvePlugin(@NotNull MavenPlugin plugin,
                                          @NotNull List<MavenRemoteRepository> repositories,
                                          int nativeMavenProjectId,
                                          boolean transitive) throws RemoteException, MavenServerProcessCanceledException;

  @NotNull
  MavenServerExecutionResult execute(@NotNull File file,
                                     @NotNull Collection<String> activeProfiles,
                                     @NotNull Collection<String> inactiveProfiles,
                                     @NotNull List<String> goals,
                                     @NotNull final List<String> selectedProjects,
                                     boolean alsoMake,
                                     boolean alsoMakeDependents) throws RemoteException, MavenServerProcessCanceledException;

  void reset() throws RemoteException;

  void release() throws RemoteException;

  void clearCaches() throws RemoteException;

  void clearCachesFor(MavenId projectId) throws RemoteException;
}
