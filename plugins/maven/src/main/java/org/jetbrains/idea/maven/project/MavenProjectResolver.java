// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

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
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.CleanBrokenArtifactsAndReimportQuickFix;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.MavenConfigParseException;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;

public class MavenProjectResolver {
  public static final Key<Collection<MavenArtifact>> UNRESOLVED_ARTIFACTS = new Key<>("Unresolved Artifacts");

  private final MavenProjectsTree myTree;
  private final Project myProject;

  public MavenProjectResolver(@Nullable MavenProjectsTree tree) {
    myTree = tree;
    myProject = tree == null ? null : tree.getProject();
  }

  @TestOnly
  public void resolve(@NotNull Project project,
                      @NotNull MavenProject mavenProject,
                      @NotNull MavenGeneralSettings generalSettings,
                      @NotNull MavenEmbeddersManager embeddersManager,
                      @NotNull MavenConsole console,
                      @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    resolve(project, Collections.singletonList(mavenProject), generalSettings, embeddersManager, console, new ResolveContext(), process);
  }

  public void resolve(@NotNull Project project,
                      @NotNull Collection<MavenProject> mavenProjects,
                      @NotNull MavenGeneralSettings generalSettings,
                      @NotNull MavenEmbeddersManager embeddersManager,
                      @NotNull MavenConsole console,
                      @NotNull ResolveContext context,
                      @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    MultiMap<File, MavenProject> projectMultiMap = groupByBasedir(mavenProjects);

    for (Map.Entry<File, Collection<MavenProject>> entry : projectMultiMap.entrySet()) {
      String baseDir = entry.getKey().getPath();
      MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, baseDir, baseDir);
      try {
        Properties userProperties = new Properties();
        for (MavenProject mavenProject : mavenProjects) {
          mavenProject.setConfigFileError(null);
          for (MavenImporter mavenImporter : mavenProject.getSuitableImporters()) {
            mavenImporter.customizeUserProperties(project, mavenProject, userProperties);
          }
        }
        embedder.customizeForResolve(myTree.getWorkspaceMap(), console, process, generalSettings.isAlwaysUpdateSnapshots(), userProperties);
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

      MavenUtil.restartConfigHighlightning(project, mavenProjects);
    }
    if (myTree != null) myTree.resolutionCompleted();
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

    MavenResolveResultProcessor.ResolveProblem problem = MavenResolveResultProcessor.getProblem(results);
    if (!problem.isEmpty()) {
      MavenResolveResultProcessor.notifySyncForProblem(project, problem);
    }
    context.putUserData(UNRESOLVED_ARTIFACTS, problem.unresolvedArtifacts);

    for (MavenProjectReaderResult result : results) {
      MavenProject mavenProjectCandidate = null;
      for (MavenProject mavenProject : mavenProjects) {
        MavenId mavenId = result.mavenModel.getMavenId();
        if (mavenProject.getMavenId().equals(mavenId)) {
          mavenProjectCandidate = mavenProject;
          break;
        }
        else if (mavenProject.getMavenId().equals(mavenId.getGroupId(), mavenId.getArtifactId())) {
          mavenProjectCandidate = mavenProject;
        }
      }

      if (mavenProjectCandidate == null) continue;

      MavenProjectChanges changes = mavenProjectCandidate
        .set(result, generalSettings, false, MavenProjectReaderResult.shouldResetDependenciesAndFolders(result), false);
      if (result.nativeMavenProject != null) {
        for (MavenImporter eachImporter : mavenProjectCandidate.getSuitableImporters()) {
          eachImporter.resolve(project, mavenProjectCandidate, result.nativeMavenProject, embedder, context);
        }
      }
      myTree.fireProjectResolved(Pair.create(mavenProjectCandidate, changes), result.nativeMavenProject);
    }
  }

  public Map<MavenPlugin, @Nullable Path> resolvePlugins(@NotNull Project project,
                                                         @NotNull MavenProject mavenProject,
                                                         @NotNull NativeMavenProjectHolder nativeMavenProject,
                                                         @NotNull MavenEmbeddersManager embeddersManager,
                                                         @NotNull MavenConsole console,
                                                         @NotNull MavenProgressIndicator process,
                                                         boolean reportUnresolvedToSyncConsole) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(mavenProject, MavenEmbeddersManager.FOR_PLUGINS_RESOLVE);
    embedder.customizeForResolve(console, process);
    embedder.clearCachesFor(mavenProject.getMavenId());

    Set<Path> filesToRefresh = new HashSet<>();
    Map<MavenPlugin, @Nullable Path> unresolvedPlugins = new HashMap<>();
    try {
      process.setText(MavenProjectBundle.message("maven.downloading.pom.plugins", mavenProject.getDisplayName()));


      for (MavenPlugin each : mavenProject.getDeclaredPlugins()) {
        process.checkCanceled();

        Collection<MavenArtifact> artifacts = embedder.resolvePlugin(each, mavenProject.getRemoteRepositories(), nativeMavenProject, false);

        for (MavenArtifact artifact : artifacts) {
          Path pluginJar = artifact.getFile().toPath();
          Path pluginDir = pluginJar.getParent();
          if (pluginDir != null) {
            filesToRefresh.add(pluginDir); // Refresh both *.pom and *.jar files.
          }
        }
        if (artifacts.isEmpty() && myProject != null) {
          unresolvedPlugins.put(each, MavenUtil.getRepositoryParentFile(project, each.getMavenId()));
        }
      }
      if(reportUnresolvedToSyncConsole) {
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

  private void reportUnresolvedPlugins(Map<MavenPlugin, @Nullable Path> unresolvedPlugins) {
    if (!unresolvedPlugins.isEmpty()) {
      Collection<@Nullable Path> files = unresolvedPlugins.values();
      CleanBrokenArtifactsAndReimportQuickFix fix = new CleanBrokenArtifactsAndReimportQuickFix(files);
      for (MavenPlugin mavenPlugin : unresolvedPlugins.keySet()) {
        MavenProjectsManager.getInstance(myProject)
          .getSyncConsole().getListener(MavenServerProgressIndicator.ResolveType.PLUGIN)
          .showBuildIssue(mavenPlugin.getMavenId().getKey(), fix);
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

  public MavenArtifactDownloader.DownloadResult downloadSourcesAndJavadocs(@NotNull Project project,
                                                                           @NotNull Collection<MavenProject> projects,
                                                                           @Nullable Collection<MavenArtifact> artifacts,
                                                                           boolean downloadSources,
                                                                           boolean downloadDocs,
                                                                           @NotNull MavenEmbeddersManager embeddersManager,
                                                                           @NotNull MavenConsole console,
                                                                           @NotNull MavenProgressIndicator process)
    throws MavenProcessCanceledException {
    MultiMap<File, MavenProject> projectMultiMap = groupByBasedir(projects);
    MavenArtifactDownloader.DownloadResult result = new MavenArtifactDownloader.DownloadResult();
    for (Map.Entry<File, Collection<MavenProject>> entry : projectMultiMap.entrySet()) {
      String baseDir = entry.getKey().getPath();
      MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DOWNLOAD, baseDir, baseDir);
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
  private MultiMap<File, MavenProject> groupByBasedir(@NotNull Collection<MavenProject> projects) {
    return ContainerUtil.groupBy(projects, p -> MavenUtil.getBaseDir(myTree.findRootProject(p).getDirectoryFile()).toFile());
  }

  public void executeWithEmbedder(@NotNull MavenProject mavenProject,
                                  @NotNull MavenEmbeddersManager embeddersManager,
                                  @NotNull Key embedderKind,
                                  @NotNull MavenConsole console,
                                  @NotNull MavenProgressIndicator process,
                                  @NotNull EmbedderTask task) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(mavenProject, embedderKind);
    embedder.customizeForResolve(myTree.getWorkspaceMap(), console, process, false);
    embedder.clearCachesFor(mavenProject.getMavenId());
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
