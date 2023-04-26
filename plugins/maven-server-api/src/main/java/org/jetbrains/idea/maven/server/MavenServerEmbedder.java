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
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;

public interface MavenServerEmbedder extends Remote {
  String MAVEN_EMBEDDER_VERSION = "idea.maven.embedder.version";
  String MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS = "idea.maven.embedder.ext.cli.args";
  String MAVEN_EXT_CLASS_PATH = "maven.ext.class.path";


  @Nullable
  MavenServerPullProgressIndicator customizeAndGetProgressIndicator(@Nullable MavenWorkspaceMap workspaceMap,
                                                                    boolean alwaysUpdateSnapshots,
                                                                    @Nullable Properties userProperties, MavenToken token)
    throws RemoteException;

  @NotNull
  Collection<MavenServerExecutionResult> resolveProject(@NotNull String longRunningTaskId,
                                                        @NotNull Collection<File> files,
                                                        @NotNull Collection<String> activeProfiles,
                                                        @NotNull Collection<String> inactiveProfiles, MavenToken token)
    throws
    RemoteException,
    MavenServerProcessCanceledException;

  @Nullable
  String evaluateEffectivePom(@NotNull File file,
                              @NotNull List<String> activeProfiles,
                              @NotNull List<String> inactiveProfiles, MavenToken token) throws RemoteException,
                                                                                               MavenServerProcessCanceledException;

  @NotNull
  List<MavenArtifact> resolve(@NotNull String longRunningTaskId,
                              @NotNull Collection<MavenArtifactResolutionRequest> requests,
                              MavenToken token) throws RemoteException, MavenServerProcessCanceledException;

  /**
   * @deprecated use {@link Maven3XServerEmbedder#resolveArtifactTransitively()}
   */
  @Deprecated
  @NotNull
  List<MavenArtifact> resolveTransitively(@NotNull List<MavenArtifactInfo> artifacts,
                                          @NotNull List<MavenRemoteRepository> remoteRepositories, MavenToken token) throws
                                                                                                                     RemoteException,
                                                                                                                     MavenServerProcessCanceledException;

  List<PluginResolutionResponse> resolvePlugins(@NotNull Collection<PluginResolutionRequest> pluginResolutionRequests,
                                                MavenToken token)
    throws RemoteException, MavenServerProcessCanceledException;

  @NotNull
  List<MavenGoalExecutionResult> executeGoal(@NotNull String longRunningTaskId,
                                             @NotNull Collection<MavenGoalExecutionRequest> requests,
                                             @NotNull String goal,
                                             MavenToken token)
    throws RemoteException, MavenServerProcessCanceledException;

  void reset(MavenToken token) throws RemoteException;

  void release(MavenToken token) throws RemoteException;

  @Nullable
  MavenModel readModel(File file, MavenToken token) throws RemoteException;

  Set<MavenRemoteRepository> resolveRepositories(@NotNull Collection<MavenRemoteRepository> repositories, MavenToken token)
    throws RemoteException;

  Collection<MavenArchetype> getArchetypes(MavenToken token) throws RemoteException;

  Collection<MavenArchetype> getLocalArchetypes(MavenToken token, @NotNull String path) throws RemoteException;

  Collection<MavenArchetype> getRemoteArchetypes(MavenToken token, @NotNull String url) throws RemoteException;

  @Nullable
  Map<String, String> resolveAndGetArchetypeDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                                       @NotNull List<MavenRemoteRepository> repositories,
                                                       @Nullable String url, MavenToken token) throws RemoteException;

  @NotNull
  MavenArtifactResolveResult resolveArtifactTransitively(
    @NotNull List<MavenArtifactInfo> artifacts,
    @NotNull List<MavenRemoteRepository> remoteRepositories,
    MavenToken token) throws RemoteException;

  @NotNull
  LongRunningTaskStatus getLongRunningTaskStatus(@NotNull String longRunningTaskId, MavenToken token) throws RemoteException;

  boolean cancelLongRunningTask(@NotNull String longRunningTaskId, MavenToken token) throws RemoteException;
}
