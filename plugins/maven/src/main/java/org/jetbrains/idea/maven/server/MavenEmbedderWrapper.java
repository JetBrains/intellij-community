// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.server.RemotePathTransformerFactory.Transformer;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

public abstract class MavenEmbedderWrapper extends MavenRemoteObjectWrapper<MavenServerEmbedder> {
  private final Project myProject;

  MavenEmbedderWrapper(@NotNull Project project) {
    super(null);
    myProject = project;
  }

  @Override
  @NotNull
  protected synchronized MavenServerEmbedder getOrCreateWrappee() throws RemoteException {
    var embedder = super.getOrCreateWrappee();
    try {
      embedder.ping(ourToken);
    }
    catch (RemoteException e) {
      onError();
      embedder = super.getOrCreateWrappee();
    }
    return embedder;
  }

  private MavenWorkspaceMap convertWorkspaceMap(@Nullable MavenWorkspaceMap map) {
    if (null == map) return null;
    Transformer transformer = RemotePathTransformerFactory.createForProject(myProject);
    if (transformer == Transformer.ID) return map;
    return MavenWorkspaceMap.copy(map, transformer::toRemotePath);
  }

  @NotNull
  public Collection<MavenServerExecutionResult> resolveProject(@NotNull Collection<VirtualFile> files,
                                                               @NotNull MavenExplicitProfiles explicitProfiles,
                                                               @Nullable ProgressIndicator indicator,
                                                               @Nullable MavenSyncConsole syncConsole,
                                                               @Nullable MavenConsole console,
                                                               @Nullable MavenWorkspaceMap workspaceMap,
                                                               boolean updateSnapshots)
    throws MavenProcessCanceledException {
    Transformer transformer = files.isEmpty() ?
                              Transformer.ID :
                              RemotePathTransformerFactory.createForProject(myProject);
    List<File> ioFiles = ContainerUtil.map(files, file -> new File(transformer.toRemotePath(file.getPath())));
    MavenWorkspaceMap serverWorkspaceMap = convertWorkspaceMap(workspaceMap);
    var request = new ProjectResolutionRequest(
      ioFiles,
      explicitProfiles.getEnabledProfiles(),
      explicitProfiles.getDisabledProfiles(),
      serverWorkspaceMap,
      updateSnapshots
    );

    var results = runLongRunningTask(
      (embedder, taskId) -> embedder.resolveProjects(taskId, request, ourToken), indicator, syncConsole, console);

    if (transformer != Transformer.ID) {
      for (MavenServerExecutionResult result : results) {
        MavenServerExecutionResult.ProjectData data = result.projectData;
        if (data == null) continue;
        new MavenBuildPathsChange((String s) -> transformer.toIdePath(s), s -> transformer.canBeRemotePath(s)).perform(data.mavenModel);
      }
    }
    return results;
  }

  @Nullable
  public String evaluateEffectivePom(@NotNull VirtualFile file,
                                     @NotNull Collection<String> activeProfiles,
                                     @NotNull Collection<String> inactiveProfiles) throws MavenProcessCanceledException {
    return evaluateEffectivePom(new File(file.getPath()), activeProfiles, inactiveProfiles);
  }

  @Nullable
  public String evaluateEffectivePom(@NotNull File file,
                                     @NotNull Collection<String> activeProfiles,
                                     @NotNull Collection<String> inactiveProfiles) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee()
      .evaluateEffectivePom(file, new ArrayList<>(activeProfiles), new ArrayList<>(inactiveProfiles), ourToken));
  }

  /**
   * @deprecated use {@link MavenEmbedderWrapper#resolveArtifacts()}
   */
  @Deprecated
  @NotNull
  public MavenArtifact resolve(@NotNull MavenArtifactInfo info,
                               @NotNull List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {
    return resolveArtifacts(List.of(new MavenArtifactResolutionRequest(info, remoteRepositories)), null, null).get(0);
  }

  @NotNull
  public List<MavenArtifact> resolveArtifacts(@NotNull Collection<MavenArtifactResolutionRequest> requests,
                                              @Nullable MavenProgressIndicator progressIndicator,
                                              @Nullable MavenConsole console) throws MavenProcessCanceledException {
    var indicator = null == progressIndicator ? null : progressIndicator.getIndicator();
    var syncConsole = null == progressIndicator ? null : progressIndicator.getSyncConsole();
    return runLongRunningTask(
      (embedder, taskId) -> embedder.resolveArtifacts(taskId, requests, ourToken), indicator, syncConsole, console
    );
  }

  /**
   * @deprecated use {@link MavenEmbedderWrapper#resolveArtifactTransitively()}
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public List<MavenArtifact> resolveTransitively(
    @NotNull final List<MavenArtifactInfo> artifacts,
    @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {

    return performCancelable(
      () -> getOrCreateWrappee().resolveArtifactsTransitively(artifacts, remoteRepositories, ourToken)).mavenResolvedArtifacts;
  }

  @NotNull
  public MavenArtifactResolveResult resolveArtifactTransitively(
    @NotNull final List<MavenArtifactInfo> artifacts,
    @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee().resolveArtifactsTransitively(artifacts, remoteRepositories, ourToken));
  }

  public List<PluginResolutionResponse> resolvePlugins(@NotNull Collection<Pair<MavenId, NativeMavenProjectHolder>> mavenPluginRequests,
                                                       @Nullable MavenProgressIndicator progressIndicator,
                                                       @Nullable MavenConsole console)
    throws MavenProcessCanceledException {
    var pluginResolutionRequests = new ArrayList<PluginResolutionRequest>();
    for (var mavenPluginRequest : mavenPluginRequests) {
      var mavenPluginId = mavenPluginRequest.first;
      try {
        var id = mavenPluginRequest.second.getId();
        pluginResolutionRequests.add(new PluginResolutionRequest(mavenPluginId, id));
      }
      catch (RemoteException e) {
        // do not call handleRemoteError here since this error occurred because of previous remote error
        MavenLog.LOG.warn("Cannot resolve plugin: " + mavenPluginId);
      }
    }

    var indicator = null == progressIndicator ? null : progressIndicator.getIndicator();
    var syncConsole = null == progressIndicator ? null : progressIndicator.getSyncConsole();
    return runLongRunningTask(
      (embedder, taskId) -> embedder.resolvePlugins(taskId, pluginResolutionRequests, ourToken), indicator, syncConsole, console);
  }

  public Collection<MavenArtifact> resolvePlugin(@NotNull MavenPlugin plugin, @NotNull NativeMavenProjectHolder nativeMavenProject)
    throws MavenProcessCanceledException {
    MavenId mavenId = plugin.getMavenId();
    return resolvePlugins(List.of(Pair.create(mavenId, nativeMavenProject)), null, null).stream()
      .flatMap(resolutionResult -> resolutionResult.getArtifacts().stream())
      .collect(Collectors.toSet());
  }

  public MavenModel readModel(final File file) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee().readModel(file, ourToken));
  }

  @NotNull
  public List<MavenGoalExecutionResult> executeGoal(@NotNull Collection<MavenGoalExecutionRequest> requests,
                                                    @NotNull String goal,
                                                    @Nullable MavenProgressIndicator progressIndicator,
                                                    @Nullable MavenConsole console)
    throws MavenProcessCanceledException {
    var indicator = null == progressIndicator ? null : progressIndicator.getIndicator();
    var syncConsole = null == progressIndicator ? null : progressIndicator.getSyncConsole();
    return runLongRunningTask(
      (embedder, taskId) -> embedder.executeGoal(taskId, requests, goal, ourToken), indicator, syncConsole, console);
  }

  @NotNull
  public Set<MavenRemoteRepository> resolveRepositories(@NotNull Collection<MavenRemoteRepository> repositories) {
    return perform(() -> getOrCreateWrappee().resolveRepositories(repositories, ourToken));
  }

  public Collection<MavenArchetype> getInnerArchetypes(@NotNull Path catalogPath) {
    return perform(() -> getOrCreateWrappee().getLocalArchetypes(ourToken, catalogPath.toString()));
  }

  public Collection<MavenArchetype> getRemoteArchetypes(@NotNull String url) {
    return perform(() -> getOrCreateWrappee().getRemoteArchetypes(ourToken, url));
  }

  @Nullable
  public Map<String, String> resolveAndGetArchetypeDescriptor(@NotNull String groupId, @NotNull String artifactId, @NotNull String version,
                                                              @NotNull List<MavenRemoteRepository> repositories,
                                                              @Nullable String url) {
    return perform(() -> getOrCreateWrappee().resolveAndGetArchetypeDescriptor(groupId, artifactId, version, repositories, url, ourToken));
  }

  public void release() {
    MavenServerEmbedder w = getWrappee();
    if (w == null) return;
    try {
      w.release(ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  /**
   * @deprecated This method does nothing (kept for a while for compatibility reasons).
   */
  // used in https://plugins.jetbrains.com/plugin/8053-azure-toolkit-for-intellij
  @Deprecated(forRemoval = true)
  public void clearCachesFor(MavenId projectId) {
  }

  protected abstract <R> R runLongRunningTask(@NotNull LongRunningEmbedderTask<R> task,
                                              @Nullable ProgressIndicator progressIndicator,
                                              @Nullable MavenSyncConsole syncConsole,
                                              @Nullable MavenConsole console) throws MavenProcessCanceledException;

  @FunctionalInterface
  protected interface LongRunningEmbedderTask<R> {
    R run(MavenServerEmbedder embedder, String longRunningTaskId) throws RemoteException, MavenServerProcessCanceledException;
  }

}
