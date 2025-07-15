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
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public interface MavenServerEmbedder extends Remote {
  String MAVEN_EMBEDDER_VERSION = "idea.maven.embedder.version";
  String MAVEN_EMBEDDER_CLI_ADDITIONAL_ARGS = "idea.maven.embedder.ext.cli.args";
  String MAVEN_EXT_CLASS_PATH = "maven.ext.class.path";

  @NotNull
  MavenServerResponse<ArrayList<MavenServerExecutionResult>> resolveProjects(
    @NotNull LongRunningTaskInput longRunningTaskInput,
    @NotNull ProjectResolutionRequest request,
    MavenToken token) throws RemoteException;

  MavenServerResponse<ArrayList<PluginResolutionResponse>> resolvePlugins(
    @NotNull LongRunningTaskInput longRunningTaskInput,
    @NotNull ArrayList<PluginResolutionRequest> pluginResolutionRequests,
    boolean forceUpdateSnapshots,
    MavenToken token) throws RemoteException;

  @NotNull
  MavenServerResponse<ArrayList<MavenArtifact>> resolveArtifacts(
    @NotNull LongRunningTaskInput longRunningTaskInput,
    @NotNull ArrayList<MavenArtifactResolutionRequest> requests,
    MavenToken token) throws RemoteException;

  @NotNull
  MavenServerResponse<@NotNull MavenArtifactResolveResult> resolveArtifactsTransitively(
    @NotNull LongRunningTaskInput longRunningTaskInput,
    @NotNull ArrayList<MavenArtifactInfo> artifacts,
    @NotNull ArrayList<MavenRemoteRepository> remoteRepositories,
    MavenToken token) throws RemoteException;
  HashSet<MavenRemoteRepository> resolveRepositories(@NotNull ArrayList<MavenRemoteRepository> repositories, MavenToken token)
    throws RemoteException;

  @NotNull
  MavenServerResponse<@NotNull String> evaluateEffectivePom(
    @NotNull LongRunningTaskInput longRunningTaskInput,
    @NotNull File file,
    @NotNull ArrayList<String> activeProfiles,
    @NotNull ArrayList<String> inactiveProfiles,
    MavenToken token) throws RemoteException;

  @NotNull
  MavenServerResponse<ArrayList<MavenGoalExecutionResult>> executeGoal(
    @NotNull LongRunningTaskInput longRunningTaskInput,
    @NotNull ArrayList<MavenGoalExecutionRequest> requests,
    @NotNull String goal,
    MavenToken token) throws RemoteException;

  @Nullable
  MavenModel readModel(File file, MavenToken token) throws RemoteException;

  ArrayList<MavenArchetype> getLocalArchetypes(MavenToken token, @NotNull String path) throws RemoteException;

  ArrayList<MavenArchetype> getRemoteArchetypes(MavenToken token, @NotNull String url) throws RemoteException;

  @Nullable
  HashMap<String, String> resolveAndGetArchetypeDescriptor(
    @NotNull String groupId,
    @NotNull String artifactId,
    @NotNull String version,
    @NotNull ArrayList<MavenRemoteRepository> repositories,
    @Nullable String url,
    MavenToken token) throws RemoteException;

  void release(MavenToken token) throws RemoteException;

  @NotNull
  LongRunningTaskStatus getLongRunningTaskStatus(@NotNull String longRunningTaskId, MavenToken token) throws RemoteException;

  boolean cancelLongRunningTask(@NotNull String longRunningTaskId, MavenToken token) throws RemoteException;

  boolean ping(MavenToken token) throws RemoteException;
}
