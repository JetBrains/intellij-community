// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public abstract class MavenEmbedderWrapper extends MavenRemoteObjectWrapper<MavenServerEmbedder> {
  private Customization myCustomization;

  public MavenEmbedderWrapper(@Nullable RemoteObjectWrapper<?> parent) {
    super(parent);
  }

  @Override
  protected synchronized void onWrappeeCreated() throws RemoteException {
    super.onWrappeeCreated();
    if (myCustomization != null) {
      doCustomize();
    }
  }

  public void customizeForResolve(MavenConsole console, MavenProgressIndicator indicator) {
    setCustomization(console, indicator, null, false, false, null);
    perform(() -> {
      doCustomize();
      return null;
    });
  }

  public void customizeForResolve(MavenWorkspaceMap workspaceMap, MavenConsole console, MavenProgressIndicator indicator, boolean alwaysUpdateSnapshot) {
    customizeForResolve(workspaceMap, console, indicator, alwaysUpdateSnapshot, null);
  }

  public void customizeForResolve(MavenWorkspaceMap workspaceMap, MavenConsole console, MavenProgressIndicator indicator,
                                  boolean alwaysUpdateSnapshot, @Nullable Properties userProperties) {
    setCustomization(console, indicator, workspaceMap, false, alwaysUpdateSnapshot, userProperties);
    perform(() -> {
      doCustomize();
      return null;
    });
  }

  public void customizeForStrictResolve(MavenWorkspaceMap workspaceMap,
                                        MavenConsole console,
                                        MavenProgressIndicator indicator) {
    setCustomization(console, indicator, workspaceMap, true, false, null);
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
    getOrCreateWrappee().customize(myCustomization.workspaceMap,
                                   myCustomization.failOnUnresolvedDependency,
                                   myCustomization.console,
                                   myCustomization.indicator,
                                   myCustomization.alwaysUpdateSnapshot || ApplicationManager.getApplication().isUnitTestMode(),
                                   myCustomization.userProperties,
                                   ourToken);
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
      final List<File> ioFiles = ContainerUtil.map(files, file -> new File(file.getPath()));
      return getOrCreateWrappee().resolveProject(ioFiles, activeProfiles, inactiveProfiles, ourToken);
    });
  }

  @Nullable
  public String evaluateEffectivePom(@NotNull final VirtualFile file,
                                     @NotNull final Collection<String> activeProfiles,
                                     @NotNull final Collection<String> inactiveProfiles) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee()
      .evaluateEffectivePom(new File(file.getPath()), new ArrayList<>(activeProfiles), new ArrayList<>(inactiveProfiles), ourToken));
  }

  @NotNull
  public MavenArtifact resolve(@NotNull final MavenArtifactInfo info,
                               @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {
    return performCancelable(() -> getOrCreateWrappee().resolve(info, remoteRepositories, ourToken));
  }

  @NotNull
  public List<MavenArtifact> resolveTransitively(
    @NotNull final List<MavenArtifactInfo> artifacts,
    @NotNull final List<MavenRemoteRepository> remoteRepositories) throws MavenProcessCanceledException {

    return performCancelable(() -> getOrCreateWrappee().resolveTransitively(artifacts, remoteRepositories, ourToken));
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
      .execute(new File(file.getPath()), activeProfiles, inactiveProfiles, goals, selectedProjects, alsoMake, alsoMakeDependents, ourToken));
  }

  public void reset() {
    MavenServerEmbedder w = getWrappee();
    if (w == null) return;
    try {
      w.reset(ourToken);
    }
    catch (RemoteException e) {
      handleRemoteError(e);
    }
    resetCustomization();
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
    resetCustomization();
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
    resetCustomization();
    myCustomization = new Customization(wrapAndExport(console),
                                        wrapAndExport(indicator),
                                        workspaceMap,
                                        failOnUnresolvedDependency,
                                        alwaysUpdateSnapshot,
                                        userProperties);
  }

  private synchronized void resetCustomization() {
    if (myCustomization == null) return;

    try {
      UnicastRemoteObject.unexportObject(myCustomization.console, true);
    }
    catch (NoSuchObjectException e) {
      MavenLog.LOG.warn(e);
    }
    try {
      UnicastRemoteObject.unexportObject(myCustomization.indicator, true);
    }
    catch (NoSuchObjectException e) {
      MavenLog.LOG.warn(e);
    }

    myCustomization = null;
  }

  private static final class Customization {
    private final MavenServerConsole console;
    private final MavenServerProgressIndicator indicator;

    private final MavenWorkspaceMap workspaceMap;
    private final boolean failOnUnresolvedDependency;
    private final boolean alwaysUpdateSnapshot;
    private final Properties userProperties;

    private Customization(MavenServerConsole console,
                          MavenServerProgressIndicator indicator,
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





  protected MavenServerConsole wrapAndExport(final MavenConsole console) {
    return doWrapAndExport(new RemoteMavenServerConsole(console));
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
