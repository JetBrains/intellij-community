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
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
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
      doCustomize();
    }
  }

  public void customizeForResolve(MavenConsole console,
                                  MavenProgressIndicator indicator,
                                  boolean forceUpdateSnapshots,
                                  @Nullable MavenWorkspaceMap workspaceMap,
                                  @Nullable Properties userProperties) {
    boolean alwaysUpdateSnapshots =
      forceUpdateSnapshots
      ? forceUpdateSnapshots
      : MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().getGeneralSettings().isAlwaysUpdateSnapshots();
    MavenWorkspaceMap serverWorkspaceMap = convertWorkspaceMap(workspaceMap);
    setCustomization(console, indicator, serverWorkspaceMap, alwaysUpdateSnapshots, userProperties);
    perform(() -> {
      doCustomize();
      return null;
    });
  }

  private MavenWorkspaceMap convertWorkspaceMap(@Nullable MavenWorkspaceMap map) {
    if (null == map) return null;
    Transformer transformer = RemotePathTransformerFactory.createForProject(myProject);
    if (transformer == Transformer.ID) return map;
    return MavenWorkspaceMap.copy(map, transformer::toRemotePath);
  }

  private synchronized void doCustomize() throws RemoteException {
    MavenServerPullProgressIndicator pullProgressIndicator =
      getOrCreateWrappee().customizeAndGetProgressIndicator(myCustomization.workspaceMap,
                                                            myCustomization.alwaysUpdateSnapshot,
                                                            myCustomization.userProperties,
                                                            ourToken);
    if (pullProgressIndicator == null) return;
    startPullingProgress(pullProgressIndicator, myCustomization.console, myCustomization.indicator);
  }

  private void startPullingProgress(MavenServerPullProgressIndicator serverPullProgressIndicator,
                                    MavenConsole console,
                                    MavenProgressIndicator indicator) {
    ScheduledFuture<?> future = myProgressPullingFuture;
    if(future!=null && !future.isCancelled()) {
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
  public Collection<MavenServerExecutionResult> resolveProject(@NotNull final Collection<VirtualFile> files,
                                                               @NotNull final Collection<String> activeProfiles,
                                                               @NotNull final Collection<String> inactiveProfiles)
    throws MavenProcessCanceledException {
    return performCancelable(() -> {
      Transformer transformer = files.isEmpty() ?
                                Transformer.ID :
                                RemotePathTransformerFactory.createForProject(myProject);
      final List<File> ioFiles = ContainerUtil.map(files, file -> new File(transformer.toRemotePath(file.getPath())));
      Collection<MavenServerExecutionResult> results =
        getOrCreateWrappee().resolveProject(ioFiles, activeProfiles, inactiveProfiles, ourToken);
      if (transformer != Transformer.ID) {
        for (MavenServerExecutionResult result : results) {
          MavenServerExecutionResult.ProjectData data = result.projectData;
          if (data == null) continue;
          new MavenBuildPathsChange((String s) -> transformer.toIdePath(s), s -> transformer.canBeRemotePath(s)).perform(data.mavenModel);
        }
      }
      return results;
    });
  }

  @Nullable
  public String evaluateEffectivePom(@NotNull final VirtualFile file,
                                     @NotNull final Collection<String> activeProfiles,
                                     @NotNull final Collection<String> inactiveProfiles) throws MavenProcessCanceledException {
    return evaluateEffectivePom(new File(file.getPath()), activeProfiles, inactiveProfiles);
  }

  @Nullable
  public String evaluateEffectivePom(@NotNull final File file,
                                     @NotNull final Collection<String> activeProfiles,
                                     @NotNull final Collection<String> inactiveProfiles) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee()
      .evaluateEffectivePom(file, new ArrayList<>(activeProfiles), new ArrayList<>(inactiveProfiles), ourToken));
  }

  @NotNull
  public MavenArtifact resolve(@NotNull final MavenArtifactInfo info,
                               @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {
    return resolve(List.of(info), remoteRepositories).get(0);
  }

  @NotNull
  public List<MavenArtifact> resolve(@NotNull Collection<MavenArtifactInfo> infos,
                                     @NotNull List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee().resolve(infos, remoteRepositories, ourToken));
  }

  /**
   * @deprecated use {@link MavenEmbedderWrapper#resolveArtifactTransitively()}
   */
  @Deprecated
  @NotNull
  public List<MavenArtifact> resolveTransitively(
    @NotNull final List<MavenArtifactInfo> artifacts,
    @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {

    return performCancelable(() -> getOrCreateWrappee().resolveTransitively(artifacts, remoteRepositories, ourToken));
  }

  @NotNull
  public MavenArtifactResolveResult resolveArtifactTransitively(
    @NotNull final List<MavenArtifactInfo> artifacts,
    @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee().resolveArtifactTransitively(artifacts, remoteRepositories, ourToken));
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
    catch (MavenServerProcessCanceledException e) {
      throw new MavenProcessCanceledException();
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
                                                    @NotNull String goal) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee().executeGoal(requests, goal, ourToken));
  }

  @NotNull
  public Set<MavenRemoteRepository> resolveRepositories(@NotNull Collection<MavenRemoteRepository> repositories) {
    return perform(() -> getOrCreateWrappee().resolveRepositories(repositories, ourToken));
  }

  public Collection<MavenArchetype> getArchetypes() {
    return perform(() -> getOrCreateWrappee().getArchetypes(ourToken));
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

  private synchronized void setCustomization(MavenConsole console,
                                             MavenProgressIndicator indicator,
                                             MavenWorkspaceMap workspaceMap,
                                             boolean alwaysUpdateSnapshot,
                                             @Nullable Properties userProperties) {
    stopPulling();
    myCustomization = new Customization(console,
                                        indicator,
                                        workspaceMap,
                                        alwaysUpdateSnapshot,
                                        userProperties);
  }

  private synchronized void stopPulling() {
    if (myProgressPullingFuture != null) {
      myProgressPullingFuture.cancel(true);
    }
    myCustomization = null;
  }

  private static final class Customization {
    private final MavenConsole console;
    private final MavenProgressIndicator indicator;

    private final MavenWorkspaceMap workspaceMap;
    private final boolean alwaysUpdateSnapshot;
    private final Properties userProperties;

    private Customization(MavenConsole console,
                          MavenProgressIndicator indicator,
                          MavenWorkspaceMap workspaceMap,
                          boolean alwaysUpdateSnapshot,
                          @Nullable Properties userProperties) {
      this.console = console;
      this.indicator = indicator;
      this.workspaceMap = workspaceMap;
      this.alwaysUpdateSnapshot = alwaysUpdateSnapshot;
      this.userProperties = userProperties;
    }
  }

}
