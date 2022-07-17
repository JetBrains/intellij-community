// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.project.Project;
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

public abstract class MavenEmbedderWrapper extends MavenRemoteObjectWrapper<MavenServerEmbedder> {
  private Customization myCustomization;
  private final Project myProject;
  private ScheduledFuture<?> myProgressPullingFuture;
  private AtomicInteger myFails = new AtomicInteger(0);

  public MavenEmbedderWrapper(@NotNull Project project, @Nullable RemoteObjectWrapper<?> parent) {
    super(parent);
    myProject = project;
  }

  @Override
  protected synchronized void onWrappeeCreated() throws RemoteException {
    super.onWrappeeCreated();
    if (myCustomization != null) {
      doCustomize();
    }
  }

  public void customizeForResolve(MavenConsole console, MavenProgressIndicator indicator) {
    boolean alwaysUpdateSnapshots =
      MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().getGeneralSettings().isAlwaysUpdateSnapshots();
    setCustomization(console, indicator, null, false, alwaysUpdateSnapshots, null);
    perform(() -> {
      doCustomize();
      return null;
    });
  }

  public void customizeForResolve(MavenConsole console, MavenProgressIndicator indicator, boolean forceUpdateSnapshots) {
    boolean alwaysUpdateSnapshots =
      forceUpdateSnapshots
      ? forceUpdateSnapshots
      : MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().getGeneralSettings().isAlwaysUpdateSnapshots();
    setCustomization(console, indicator, null, false, alwaysUpdateSnapshots, null);
    perform(() -> {
      doCustomize();
      return null;
    });
  }

  public void customizeForResolve(MavenWorkspaceMap workspaceMap,
                                  MavenConsole console,
                                  MavenProgressIndicator indicator,
                                  boolean alwaysUpdateSnapshot) {
    customizeForResolve(workspaceMap, console, indicator, alwaysUpdateSnapshot, null);
  }

  public void customizeForResolve(MavenWorkspaceMap workspaceMap, MavenConsole console, MavenProgressIndicator indicator,
                                  boolean alwaysUpdateSnapshot, @Nullable Properties userProperties) {

    MavenWorkspaceMap serverWorkspaceMap = convertWorkspaceMap(workspaceMap);
    setCustomization(console, indicator, serverWorkspaceMap, false, alwaysUpdateSnapshot, userProperties);
    perform(() -> {
      doCustomize();
      return null;
    });
  }

  private MavenWorkspaceMap convertWorkspaceMap(MavenWorkspaceMap map) {
    Transformer transformer = RemotePathTransformerFactory.createForProject(myProject);
    if (transformer == Transformer.ID) return map;
    return MavenWorkspaceMap.copy(map, transformer::toRemotePath);
  }

  public void customizeForStrictResolve(MavenWorkspaceMap workspaceMap,
                                        MavenConsole console,
                                        MavenProgressIndicator indicator) {
    MavenWorkspaceMap serverWorkspaceMap = convertWorkspaceMap(workspaceMap);
    boolean alwaysUpdateSnapshots =
      MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().getGeneralSettings().isAlwaysUpdateSnapshots();
    setCustomization(console, indicator, serverWorkspaceMap, true, alwaysUpdateSnapshots, null);
    perform(() -> {
      doCustomize();
      return null;
    });
  }

  public void customizeForGetVersions() {
    perform(() -> {
      doCustomizeComponents();
      return null;
    });
  }

  private synchronized void doCustomizeComponents() throws RemoteException {
    getOrCreateWrappee().customizeComponents(ourToken);
  }

  private synchronized void doCustomize() throws RemoteException {
    MavenServerPullProgressIndicator pullProgressIndicator =
      getOrCreateWrappee().customizeAndGetProgressIndicator(myCustomization.workspaceMap,
                                                            myCustomization.failOnUnresolvedDependency,
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
              case DOWNLOAD_STARTED:
                indicator.startedDownload(e.getResolveType(), e.getDependencyId());
                break;
              case DOWNLOAD_COMPLETED:
                indicator.completedDownload(e.getResolveType(), e.getDependencyId());
                break;
              case DOWNLOAD_FAILED:
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
       MavenLog.LOG.warn("Maven embedder download listener was failed: " + count + " times");
    }
    super.cleanup();
  }

  public MavenServerExecutionResult resolveProject(@NotNull final VirtualFile file,
                                                   @NotNull final Collection<String> activeProfiles,
                                                   @NotNull final Collection<String> inactiveProfiles)
    throws MavenProcessCanceledException {
    return resolveProject(Collections.singleton(file), activeProfiles, inactiveProfiles).iterator().next();
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
    return performCancelable(() -> getOrCreateWrappee().resolve(info, remoteRepositories, ourToken));
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

  @NotNull
  public List<String> retrieveVersions(@NotNull final String groupId,
                                       @NotNull final String artifactId,
                                       @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {

    return performCancelable(() -> getOrCreateWrappee().retrieveAvailableVersions(groupId, artifactId, remoteRepositories, ourToken));
  }

  public Collection<MavenArtifact> resolvePlugin(@NotNull final MavenPlugin plugin,
                                                 @NotNull final List<MavenRemoteRepository> repositories,
                                                 @NotNull final NativeMavenProjectHolder nativeMavenProject,
                                                 final boolean transitive) throws MavenProcessCanceledException {
    int id;
    try {
      id = nativeMavenProject.getId();
    }
    catch (RemoteException e) {
      // do not call handleRemoteError here since this error occurred because of previous remote error
      return Collections.emptyList();
    }

    try {
      return getOrCreateWrappee().resolvePlugin(plugin, repositories, id, transitive, ourToken);
    }
    catch (RemoteException e) {
      // do not try to reconnect here since we have lost NativeMavenProjectHolder anyway.
      handleRemoteError(e);
      return Collections.emptyList();
    }
    catch (MavenServerProcessCanceledException e) {
      throw new MavenProcessCanceledException();
    }
  }

  public MavenModel readModel(final File file) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee().readModel(file, ourToken));
  }

  @NotNull
  public MavenServerExecutionResult execute(@NotNull final VirtualFile file,
                                            @NotNull final Collection<String> activeProfiles,
                                            @NotNull final Collection<String> inactiveProfiles,
                                            @NotNull final List<String> goals) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee()
      .execute(new File(file.getPath()), activeProfiles, inactiveProfiles, goals, Collections.emptyList(), false, false, ourToken));
  }

  @NotNull
  public MavenServerExecutionResult execute(@NotNull final VirtualFile file,
                                            @NotNull final Collection<String> activeProfiles,
                                            @NotNull final Collection<String> inactiveProfiles,
                                            @NotNull final List<String> goals,
                                            @NotNull final List<String> selectedProjects,
                                            final boolean alsoMake,
                                            final boolean alsoMakeDependents) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee()
      .execute(new File(file.getPath()), activeProfiles, inactiveProfiles, goals, selectedProjects, alsoMake, alsoMakeDependents,
               ourToken));
  }

  @NotNull
  public Set<MavenRemoteRepository> resolveRepositories(@NotNull Collection<MavenRemoteRepository> repositories)
    throws MavenProcessCanceledException {
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


  public void clearCaches() {
    MavenServerEmbedder w = getWrappee();
    if (w == null) return;
    try {
      w.clearCaches(ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  public void clearCachesFor(MavenId projectId) {
    MavenServerEmbedder w = getWrappee();
    if (w == null) return;
    try {
      w.clearCachesFor(projectId, ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
  }

  private synchronized void setCustomization(MavenConsole console,
                                             MavenProgressIndicator indicator,
                                             MavenWorkspaceMap workspaceMap,
                                             boolean failOnUnresolvedDependency,
                                             boolean alwaysUpdateSnapshot,
                                             @Nullable Properties userProperties) {
    stopPulling();
    myCustomization = new Customization(console,
                                        indicator,
                                        workspaceMap,
                                        failOnUnresolvedDependency,
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
    private final boolean failOnUnresolvedDependency;
    private final boolean alwaysUpdateSnapshot;
    private final Properties userProperties;

    private Customization(MavenConsole console,
                          MavenProgressIndicator indicator,
                          MavenWorkspaceMap workspaceMap,
                          boolean failOnUnresolvedDependency,
                          boolean alwaysUpdateSnapshot,
                          @Nullable Properties userProperties) {
      this.console = console;
      this.indicator = indicator;
      this.workspaceMap = workspaceMap;
      this.failOnUnresolvedDependency = failOnUnresolvedDependency;
      this.alwaysUpdateSnapshot = alwaysUpdateSnapshot;
      this.userProperties = userProperties;
    }
  }


  private static class RemoteMavenServerConsole extends MavenRemoteObject implements MavenServerConsole {
    private final MavenConsole myConsole;

    RemoteMavenServerConsole(MavenConsole console) {
      myConsole = console;
    }

    @Override
    public void printMessage(int level, String message, Throwable throwable) {
      myConsole.printMessage(level, message, throwable);
    }
  }
}
