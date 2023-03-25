// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.ide.plugins.advertiser.PluginFeatureEnabler;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.server.MavenConfigParseException;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.*;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class MavenProjectResolver {
  public static final Key<Collection<MavenArtifact>> UNRESOLVED_ARTIFACTS = new Key<>("Unresolved Artifacts");

  private @NotNull MavenResolvedPluginsCache myCacheResolver;

  private final MavenProjectsTree myTree;
  private final Project myProject;

  public MavenProjectResolver(@Nullable MavenProjectsTree tree) {
    myTree = tree;
    myProject = tree == null ? null : tree.getProject();
    myCacheResolver = new MavenResolvedPluginsCache();
  }

  @TestOnly
  public void resolve(@NotNull Project project,
                      @NotNull MavenProject mavenProject,
                      @NotNull MavenGeneralSettings generalSettings,
                      @NotNull MavenEmbeddersManager embeddersManager,
                      @NotNull MavenConsole console,
                      @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    resolve(project, Collections.singletonList(mavenProject), generalSettings, embeddersManager, console, new ResolveContext(myTree), process);
  }

  public void resolve(@NotNull Project project,
                      @NotNull Collection<MavenProject> mavenProjects,
                      @NotNull MavenGeneralSettings generalSettings,
                      @NotNull MavenEmbeddersManager embeddersManager,
                      @NotNull MavenConsole console,
                      @NotNull ResolveContext context,
                      @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    MultiMap<Path, MavenProject> projectMultiMap = groupByBasedir(mavenProjects);

    for (Map.Entry<Path, Collection<MavenProject>> entry : projectMultiMap.entrySet()) {
      String baseDir = entry.getKey().toString();
      MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, baseDir);
      try {
        Properties userProperties = new Properties();
        for (MavenProject mavenProject : mavenProjects) {
          mavenProject.setConfigFileError(null);
          for (MavenImporter mavenImporter : MavenImporter.getSuitableImporters(mavenProject)) {
            mavenImporter.customizeUserProperties(project, mavenProject, userProperties);
          }
        }
        boolean updateSnapshots = MavenProjectsManager.getInstance(project).getForceUpdateSnapshots();
        updateSnapshots = updateSnapshots ? updateSnapshots : generalSettings.isAlwaysUpdateSnapshots();
        embedder.customizeForResolve(myTree.getWorkspaceMap(), console, process, updateSnapshots, userProperties);
        doResolve(project, entry.getValue(), generalSettings, embedder, context, process);
      }
      catch (Throwable t) {
        MavenConfigParseException cause = findParseException(t);
        if (cause != null) {

          MavenLog.LOG.warn("Cannot parse maven config", cause);
        }
        else {
          throw t;
        }
      }
      finally {
        embeddersManager.release(embedder);
      }

      MavenUtil.restartConfigHighlighting(mavenProjects);
    }
  }

  public void clearPluginCache() {
    myCacheResolver = new MavenResolvedPluginsCache();
  }


  private static MavenConfigParseException findParseException(Throwable t) {
    MavenConfigParseException parseException = ExceptionUtil.findCause(t, MavenConfigParseException.class);
    if (parseException != null) {
      return parseException;
    }

    Throwable cause = ExceptionUtil.getRootCause(t);
    if (cause instanceof InvocationTargetException) {
      Throwable target = ((InvocationTargetException)cause).getTargetException();
      if (target != null) {
        return ExceptionUtil.findCause(target, MavenConfigParseException.class);
      }
    }
    return null;
  }

  private void doResolve(@NotNull Project project,
                         @NotNull Collection<MavenProject> mavenProjects,
                         @NotNull MavenGeneralSettings generalSettings,
                         @NotNull MavenEmbedderWrapper embedder,
                         @NotNull ResolveContext context,
                         @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    if (mavenProjects.isEmpty()) return;

    process.checkCanceled();
    final List<String> names = ContainerUtil.mapNotNull(mavenProjects, p -> p.getDisplayName());
    final String text = StringUtil.shortenPathWithEllipsis(StringUtil.join(names, ", "), 200);
    process.setText(MavenProjectBundle.message("maven.resolving.pom", text));
    process.setText2("");

    MavenExplicitProfiles explicitProfiles = myTree.getExplicitProfiles();
    Collection<VirtualFile> files = ContainerUtil.map(mavenProjects, p -> p.getFile());
    Collection<MavenProjectReaderResult> results = new MavenProjectReader(project)
      .resolveProject(generalSettings, embedder, files, explicitProfiles, myTree.getProjectLocator());

    MavenResolveResultProblemProcessor.MavenResolveProblemHolder problems = MavenResolveResultProblemProcessor.getProblems(results);
    MavenResolveResultProblemProcessor.notifySyncForProblem(project, problems);

    context.putUserData(UNRESOLVED_ARTIFACTS, problems.unresolvedArtifacts);

    doResolve(project, results, mavenProjects, generalSettings, embedder, context);
  }

  private void doResolve(@NotNull Project project,
                         @NotNull Collection<MavenProjectReaderResult> results,
                         @NotNull Collection<MavenProject> mavenProjects,
                         @NotNull MavenGeneralSettings generalSettings,
                         @NotNull MavenEmbedderWrapper embedder,
                         @NotNull ResolveContext context) throws MavenProcessCanceledException {
    var artifactIdToMavenProjects = mavenProjects.stream()
      .filter(mavenProject -> null != mavenProject.getMavenId().getArtifactId())
      .collect(Collectors.groupingBy(mavenProject -> mavenProject.getMavenId().getArtifactId()));
    var pluginResolutionRequests = new ConcurrentLinkedQueue<Pair<MavenProject, NativeMavenProjectHolder>>();
    ParallelRunner.<MavenProjectReaderResult, MavenProcessCanceledException>runInParallelRethrow(results, result -> {
      doResolve(project, result, artifactIdToMavenProjects, generalSettings, embedder, context, pluginResolutionRequests);
    });
    schedulePluginResolution(pluginResolutionRequests);
  }

  private void doResolve(@NotNull Project project,
                         @NotNull MavenProjectReaderResult result,
                         @NotNull Map<String, List<MavenProject>> artifactIdToMavenProjects,
                         @NotNull MavenGeneralSettings generalSettings,
                         @NotNull MavenEmbedderWrapper embedder,
                         @NotNull ResolveContext context,
                         @NotNull ConcurrentLinkedQueue<Pair<MavenProject, NativeMavenProjectHolder>> pluginResolutionRequests)
    throws MavenProcessCanceledException {
    var mavenId = result.mavenModel.getMavenId();
    var artifactId = mavenId.getArtifactId();

    List<MavenProject> mavenProjects = artifactIdToMavenProjects.get(artifactId);
    if (null == mavenProjects) return;

    MavenProject mavenProjectCandidate = null;
    for (MavenProject mavenProject : mavenProjects) {
      if (mavenProject.getMavenId().equals(mavenId)) {
        mavenProjectCandidate = mavenProject;
        break;
      }
      else if (mavenProject.getMavenId().equals(mavenId.getGroupId(), mavenId.getArtifactId())) {
        mavenProjectCandidate = mavenProject;
      }
    }

    if (mavenProjectCandidate == null) return;

    MavenProject.Snapshot snapshot = mavenProjectCandidate.getSnapshot();
    var resetArtifacts = MavenProjectReaderResult.shouldResetDependenciesAndFolders(result);
    mavenProjectCandidate.set(result, generalSettings, false, resetArtifacts, false);
    NativeMavenProjectHolder nativeMavenProject = result.nativeMavenProject;
    if (nativeMavenProject != null) {
      PluginFeatureEnabler.getInstance(myProject).scheduleEnableSuggested();

      for (MavenImporter eachImporter : MavenImporter.getSuitableImporters(mavenProjectCandidate)) {
        eachImporter.resolve(project, mavenProjectCandidate, nativeMavenProject, embedder, context);
      }
    }
    // project may be modified by MavenImporters, so we need to collect the changes after them:
    MavenProjectChanges changes = mavenProjectCandidate.getChangesSinceSnapshot(snapshot);

    mavenProjectCandidate.getProblems(); // need for fill problem cache
    myTree.fireProjectResolved(Pair.create(mavenProjectCandidate, changes), nativeMavenProject);

    if (null != nativeMavenProject) {
      if (!mavenProjectCandidate.hasReadingProblems() && mavenProjectCandidate.hasUnresolvedPlugins()) {
        pluginResolutionRequests.add(Pair.create(mavenProjectCandidate, nativeMavenProject));
      }
    }
  }

  private void schedulePluginResolution(@NotNull Collection<Pair<MavenProject, NativeMavenProjectHolder>> pluginResolutionRequests) {
    var projectsManager = MavenProjectsManager.getInstance(myProject);
    projectsManager.schedulePluginResolution(new MavenProjectsProcessorPluginsResolvingTask(pluginResolutionRequests, this));
  }

  public Set<MavenPlugin> resolvePlugins(@NotNull Collection<Pair<MavenProject, NativeMavenProjectHolder>> mavenProjects,
                                         @NotNull MavenEmbeddersManager embeddersManager,
                                         @NotNull MavenConsole console,
                                         @NotNull MavenProgressIndicator process,
                                         boolean reportUnresolvedToSyncConsole,
                                         boolean forceUpdateSnapshots) throws MavenProcessCanceledException {
    return resolvePluginsFromCache(mavenProjects,
                                   embeddersManager,
                                   console,
                                   process,
                                   reportUnresolvedToSyncConsole,
                                   forceUpdateSnapshots);
  }

  private Set<MavenPlugin> resolvePluginsFromCache(@NotNull Collection<Pair<MavenProject, NativeMavenProjectHolder>> mavenProjects,
                                                   @NotNull MavenEmbeddersManager embeddersManager,
                                                   @NotNull MavenConsole console,
                                                   @NotNull MavenProgressIndicator process,
                                                   boolean reportUnresolvedToSyncConsole,
                                                   boolean forceUpdateSnapshots) throws MavenProcessCanceledException {
    Set<MavenPlugin> unresolvedPlugins = new HashSet<>();

    for (var projectData : mavenProjects) {
      var plugins = resolvePlugins(projectData.first,
                                   projectData.second,
                                   embeddersManager,
                                   console,
                                   process,
                                   reportUnresolvedToSyncConsole,
                                   forceUpdateSnapshots);
      unresolvedPlugins.addAll(plugins);
    }

    return unresolvedPlugins;
  }

  private Set<MavenPlugin> resolvePlugins(@NotNull MavenProject mavenProject,
                                          @NotNull NativeMavenProjectHolder nativeMavenProject,
                                          @NotNull MavenEmbeddersManager embeddersManager,
                                          @NotNull MavenConsole console,
                                          @NotNull MavenProgressIndicator process,
                                          boolean reportUnresolvedToSyncConsole,
                                          boolean forceUpdateSnapshots) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(mavenProject, MavenEmbeddersManager.FOR_PLUGINS_RESOLVE);
    embedder.customizeForResolve(console, process, forceUpdateSnapshots);

    Set<Path> filesToRefresh = new HashSet<>();
    Set<MavenPlugin> unresolvedPlugins = new HashSet<>();
    try {
      process.setText(MavenProjectBundle.message("maven.downloading.pom.plugins", mavenProject.getDisplayName()));

      for (MavenPlugin each : mavenProject.getDeclaredPlugins()) {
        process.checkCanceled();

        MavenResolvedPluginsCache.PluginResolvedResult result = myCacheResolver.resolveCached(embedder, each, nativeMavenProject);
        if (!result.fromCache()) {
          for (MavenArtifact artifact : result.artifacts()) {
            Path pluginJar = artifact.getFile().toPath();
            Path pluginDir = pluginJar.getParent();
            if (pluginDir != null) {
              filesToRefresh.add(pluginDir); // Refresh both *.pom and *.jar files.
            }
          }
        }
        if (result.artifacts().isEmpty() && myProject != null) {
          unresolvedPlugins.add(each);
        }
      }
      if (reportUnresolvedToSyncConsole) {
        reportUnresolvedPlugins(unresolvedPlugins);
      }

      mavenProject.resetCache();
      myTree.firePluginsResolved(mavenProject);
    }
    finally {
      if (filesToRefresh.size() > 0) {
        LocalFileSystem.getInstance().refreshNioFiles(filesToRefresh);
      }

      embeddersManager.release(embedder);
    }
    return unresolvedPlugins;
  }

  private void reportUnresolvedPlugins(Set<MavenPlugin> unresolvedPlugins) {
    if (!unresolvedPlugins.isEmpty()) {
      for (MavenPlugin mavenPlugin : unresolvedPlugins) {
        MavenProjectsManager.getInstance(myProject)
          .getSyncConsole().getListener(MavenServerProgressIndicator.ResolveType.PLUGIN)
          .showArtifactBuildIssue(mavenPlugin.getMavenId().getKey(), null);
      }
    }
  }

  public void resolveFolders(@NotNull final MavenProject mavenProject,
                             @NotNull final MavenImportingSettings importingSettings,
                             @NotNull final MavenEmbeddersManager embeddersManager,
                             @NotNull final MavenConsole console,
                             @NotNull final MavenProgressIndicator process) throws MavenProcessCanceledException {
    executeWithEmbedder(mavenProject,
                        embeddersManager,
                        MavenEmbeddersManager.FOR_FOLDERS_RESOLVE,
                        console,
                        process,
                        new MavenProjectResolver.EmbedderTask() {
                          @Override
                          public void run(MavenEmbedderWrapper embedder) throws MavenProcessCanceledException {
                            process.checkCanceled();
                            process.setText(MavenProjectBundle.message("maven.updating.folders.pom", mavenProject.getDisplayName()));
                            process.setText2("");

                            Pair<Boolean, MavenProjectChanges> resolveResult = mavenProject.resolveFolders(embedder,
                                                                                                           importingSettings,
                                                                                                           console);
                            if (resolveResult.first) {
                              myTree.fireFoldersResolved(Pair.create(mavenProject, resolveResult.second));
                            }
                          }
                        });
  }

  public @NotNull MavenArtifactDownloader.DownloadResult downloadSourcesAndJavadocs(@NotNull Project project,
                                                                                    @NotNull Collection<MavenProject> projects,
                                                                                    @Nullable Collection<MavenArtifact> artifacts,
                                                                                    boolean downloadSources,
                                                                                    boolean downloadDocs,
                                                                                    @NotNull MavenEmbeddersManager embeddersManager,
                                                                                    @NotNull MavenConsole console,
                                                                                    @NotNull MavenProgressIndicator process)
    throws MavenProcessCanceledException {
    MultiMap<Path, MavenProject> projectMultiMap = groupByBasedir(projects);
    MavenArtifactDownloader.DownloadResult result = new MavenArtifactDownloader.DownloadResult();
    for (Map.Entry<Path, Collection<MavenProject>> entry : projectMultiMap.entrySet()) {
      String baseDir = entry.getKey().toString();
      MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DOWNLOAD, baseDir);
      try {
        embedder.customizeForResolve(console, process);
        MavenArtifactDownloader.DownloadResult result1 =
          MavenArtifactDownloader.download(project, myTree, projects, artifacts, downloadSources, downloadDocs, embedder, process);

        for (MavenProject each : projects) {
          myTree.fireArtifactsDownloaded(each);
        }

        result.resolvedDocs.addAll(result1.resolvedDocs);
        result.resolvedSources.addAll(result1.resolvedSources);
        result.unresolvedDocs.addAll(result1.unresolvedDocs);
        result.unresolvedSources.addAll(result1.unresolvedSources);
      }
      finally {
        embeddersManager.release(embedder);
      }
    }
    return result;
  }

  @NotNull
  private MultiMap<Path, MavenProject> groupByBasedir(@NotNull Collection<MavenProject> projects) {
    return ContainerUtil.groupBy(projects, p -> MavenUtil.getBaseDir(myTree.findRootProject(p).getDirectoryFile()));
  }

  public void executeWithEmbedder(@NotNull MavenProject mavenProject,
                                  @NotNull MavenEmbeddersManager embeddersManager,
                                  @NotNull Key embedderKind,
                                  @NotNull MavenConsole console,
                                  @NotNull MavenProgressIndicator process,
                                  @NotNull EmbedderTask task) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(mavenProject, embedderKind);
    embedder.customizeForResolve(myTree.getWorkspaceMap(), console, process, false);
    try {
      task.run(embedder);
    }
    finally {
      embeddersManager.release(embedder);
    }
  }

  public static void showNotificationInvalidConfig(@NotNull Project project, @Nullable MavenProject mavenProject, String message) {
    VirtualFile configFile = mavenProject == null ? null : MavenUtil.getConfigFile(mavenProject, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
    if (configFile != null) {
      new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, RunnerBundle.message("maven.invalid.config.file.with.link", message),
                       NotificationType.ERROR)
        .setListener((notification, event) -> FileEditorManager.getInstance(project).openFile(configFile, true))
        .notify(project);
    }
    else {
      new Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP, "", RunnerBundle.message("maven.invalid.config.file", message),
                       NotificationType.ERROR)
        .notify(project);
    }
  }

  public interface EmbedderTask {
    void run(MavenEmbedderWrapper embedder) throws MavenProcessCanceledException;
  }
}
