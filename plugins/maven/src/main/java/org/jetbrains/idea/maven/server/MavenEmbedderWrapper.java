// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public abstract class MavenEmbedderWrapper extends MavenRemoteObjectWrapper<MavenServerEmbedder> {
  private Customization myCustomization;
  private final Project myProject;
  private ScheduledFuture<?> myProgressPullingFuture;
  private AtomicInteger myFails = new AtomicInteger(0);

  MavenEmbedderWrapper(@NotNull Project project) {
    super(null);
    myProject = project;
  }

  @Override
  protected synchronized void onWrappeeCreated() throws RemoteException {
    super.onWrappeeCreated();
    if (myCustomization != null) {
      MavenServerEmbedder embedder = getOrCreateWrappee();
      startPullingProgress(embedder, myCustomization.console, myCustomization.indicator);
    }
  }

  public void startPullingProgress(MavenConsole console,
                                   MavenProgressIndicator indicator) {
    stopPulling();
    myCustomization = new Customization(console, indicator);
    perform(() -> {
      MavenServerEmbedder embedder = getOrCreateWrappee();
      startPullingProgress(embedder, myCustomization.console, myCustomization.indicator);
      return null;
    });
  }

  private MavenWorkspaceMap convertWorkspaceMap(@Nullable MavenWorkspaceMap map) {
    if (null == map) return null;
    Transformer transformer = RemotePathTransformerFactory.createForProject(myProject);
    if (transformer == Transformer.ID) return map;
    return MavenWorkspaceMap.copy(map, transformer::toRemotePath);
  }

  private void startPullingProgress(MavenServerEmbedder embedder,
                                    MavenConsole console,
                                    MavenProgressIndicator indicator) throws RemoteException {
    MavenServerPullProgressIndicator serverPullProgressIndicator = embedder.getProgressIndicator(ourToken);
    ScheduledFuture<?> future = myProgressPullingFuture;
    if (future != null && !future.isCancelled()) {
      future.cancel(true);
    }
    myProgressPullingFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
      try {
        if (indicator.isCanceled()) serverPullProgressIndicator.cancel();

        List<MavenArtifactDownloadServerProgressEvent> artifactEvents = serverPullProgressIndicator.pullDownloadEvents();
        if (artifactEvents != null) {
          for (MavenArtifactDownloadServerProgressEvent e : artifactEvents) {
            switch (e.getArtifactEventType()) {
              case DOWNLOAD_STARTED -> indicator.startedDownload(e.getResolveType(), e.getDependencyId());
              case DOWNLOAD_COMPLETED -> indicator.completedDownload(e.getResolveType(), e.getDependencyId());
              case DOWNLOAD_FAILED ->
                indicator.failedDownload(e.getResolveType(), e.getDependencyId(), e.getErrorMessage(), e.getStackTrace());
            }
          }
        }

        List<MavenServerConsoleEvent> consoleEvents = serverPullProgressIndicator.pullConsoleEvents();
        if (consoleEvents != null) {
          for (MavenServerConsoleEvent e : consoleEvents) {
            console.printMessage(e.getLevel(), e.getMessage(), e.getThrowable());
          }
        }
        myFails.set(0);
      }
      catch (RemoteException e) {
        if (!Thread.currentThread().isInterrupted()) {
          myFails.incrementAndGet();
        }
      }
    }, 500, 500, TimeUnit.MILLISECONDS);
  }

  @Override
  protected synchronized void cleanup() {
    if (myProgressPullingFuture != null) myProgressPullingFuture.cancel(true);
    int count = myFails.get();
    if (count != 0) {
       MavenLog.LOG.warn("Maven embedder download listener failed: " + count + " times");
    }
    super.cleanup();
  }

  @NotNull
  public Collection<MavenServerExecutionResult> resolveProject(@NotNull Collection<VirtualFile> files,
                                                               @NotNull MavenExplicitProfiles explicitProfiles,
                                                               @Nullable MavenProgressIndicator progressIndicator,
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

    var results = runLongRunningTask((embedder, taskId) -> embedder.resolveProjects(taskId, request, ourToken), progressIndicator);

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
    return resolveArtifacts(List.of(new MavenArtifactResolutionRequest(info, remoteRepositories)), null).get(0);
  }

  @NotNull
  public List<MavenArtifact> resolveArtifacts(@NotNull Collection<MavenArtifactResolutionRequest> requests,
                                              @Nullable MavenProgressIndicator progressIndicator) throws MavenProcessCanceledException {
    return runLongRunningTask(
      (embedder, longRunningTaskId) -> embedder.resolveArtifacts(longRunningTaskId, requests, ourToken), progressIndicator
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

  public List<PluginResolutionResponse> resolvePlugins(@NotNull Collection<Pair<MavenId, NativeMavenProjectHolder>> mavenPluginRequests)
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

    try {
      return getOrCreateWrappee().resolvePlugins(pluginResolutionRequests, ourToken);
    }
    catch (RemoteException e) {
      // do not try to reconnect here since we have lost NativeMavenProjectHolder anyway.
      handleRemoteError(e);
      return ContainerUtil.map(mavenPluginRequests, request -> new PluginResolutionResponse(request.first, false, List.of()));
    }
  }

  public Collection<MavenArtifact> resolvePlugin(@NotNull final MavenPlugin plugin,
                                                 @NotNull final NativeMavenProjectHolder nativeMavenProject)
    throws MavenProcessCanceledException {
    MavenId mavenId = plugin.getMavenId();
    return resolvePlugins(List.of(Pair.create(mavenId, nativeMavenProject))).stream()
      .flatMap(resolutionResult -> resolutionResult.getArtifacts().stream())
      .collect(Collectors.toSet());
  }

  public MavenModel readModel(final File file) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee().readModel(file, ourToken));
  }

  @NotNull
  public List<MavenGoalExecutionResult> executeGoal(@NotNull Collection<MavenGoalExecutionRequest> requests,
                                                    @NotNull String goal,
                                                    @Nullable MavenProgressIndicator progressIndicator)
    throws MavenProcessCanceledException {
    return runLongRunningTask(
      (embedder, longRunningTaskId) -> embedder.executeGoal(longRunningTaskId, requests, goal, ourToken), progressIndicator);
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

  public void reset() {
    MavenServerEmbedder w = getWrappee();
    if (w == null) return;
    try {
      stopPulling();
      w.reset(ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  public void release() {
    MavenServerEmbedder w = getWrappee();
    if (w == null) return;
    try {
      stopPulling();
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

  private synchronized void stopPulling() {
    if (myProgressPullingFuture != null) {
      myProgressPullingFuture.cancel(true);
    }
    myCustomization = null;
  }

  protected abstract <R> R runLongRunningTask(@NotNull LongRunningTask<R> task,
                                              @Nullable MavenProgressIndicator progressIndicator) throws MavenProcessCanceledException;

  @FunctionalInterface
  protected interface LongRunningTask<R> {
    R run(MavenServerEmbedder embedder, String longRunningTaskId) throws RemoteException, MavenServerProcessCanceledException;
  }

  private static final class Customization {
    private final MavenConsole console;
    private final MavenProgressIndicator indicator;

    private Customization(MavenConsole console,
                          MavenProgressIndicator indicator) {
      this.console = console;
      this.indicator = indicator;
    }
  }

}
