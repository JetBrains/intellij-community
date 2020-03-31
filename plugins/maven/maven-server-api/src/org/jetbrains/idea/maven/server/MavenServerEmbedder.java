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
import java.util.Properties;
import org.jetbrains.idea.maven.server.security.MavenToken;

public interface MavenServerEmbedder extends Remote {
  String MAVEN_EMBEDDER_VERSION = "idea.maven.embedder.version";
  String MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS = "idea.maven.embedder.ext.cli.args";
  String MAVEN_EXT_CLASS_PATH = "maven.ext.class.path";

  void customize(@Nullable MavenWorkspaceMap workspaceMap,
                 boolean failOnUnresolvedDependency,
                 @NotNull MavenServerConsole console,
                 @NotNull MavenServerProgressIndicator indicator,
                 boolean alwaysUpdateSnapshots,
                 @Nullable Properties userProperties, MavenToken token) throws RemoteException;

  void customizeComponents(MavenToken token) throws RemoteException;

  @NotNull
  List<String> retrieveAvailableVersions(@NotNull String groupId,
                                         @NotNull String artifactId,
                                         @NotNull List<MavenRemoteRepository> remoteRepositories, MavenToken token)
    throws RemoteException;


  @NotNull
  Collection<MavenServerExecutionResult> resolveProject(@NotNull Collection<File> files,
                                                        @NotNull Collection<String> activeProfiles,
                                                        @NotNull Collection<String> inactiveProfiles, MavenToken token) throws RemoteException,
                                                                                                                         MavenServerProcessCanceledException;

  @Nullable
  String evaluateEffectivePom(@NotNull File file,
                              @NotNull List<String> activeProfiles,
                              @NotNull List<String> inactiveProfiles, MavenToken token) throws RemoteException,
                                                                                         MavenServerProcessCanceledException;

  @NotNull
  MavenArtifact resolve(@NotNull MavenArtifactInfo info,
                        @NotNull List<MavenRemoteRepository> remoteRepositories, MavenToken token) throws RemoteException,
                                                                                                    MavenServerProcessCanceledException;

  @NotNull
  List<MavenArtifact> resolveTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                          @NotNull List<MavenRemoteRepository> remoteRepositories, MavenToken token) throws RemoteException,
                                                                                                                      MavenServerProcessCanceledException;

  Collection<MavenArtifact> resolvePlugin(@NotNull MavenPlugin plugin,
                                          @NotNull List<MavenRemoteRepository> repositories,
                                          int nativeMavenProjectId,
                                          boolean transitive, MavenToken token) throws RemoteException, MavenServerProcessCanceledException;

  @NotNull
  MavenServerExecutionResult execute(@NotNull File file,
                                     @NotNull Collection<String> activeProfiles,
                                     @NotNull Collection<String> inactiveProfiles,
                                     @NotNull List<String> goals,
                                     @NotNull final List<String> selectedProjects,
                                     boolean alsoMake,
                                     boolean alsoMakeDependents, MavenToken token) throws RemoteException, MavenServerProcessCanceledException;

  void reset(MavenToken token) throws RemoteException;

  void release(MavenToken token) throws RemoteException;

  void clearCaches(MavenToken token) throws RemoteException;

  void clearCachesFor(MavenId projectId, MavenToken token) throws RemoteException;

  @Nullable
  MavenModel readModel(File file, MavenToken token) throws RemoteException;
}
