// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.execution.RunnerBundle;
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.jetbrains.idea.maven.project.MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE;

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
    resolve(project, Collections.singletonList(mavenProject), generalSettings, embeddersManager, console, new ResolveContext(myTree), process);
  }

  public void resolve(@NotNull Project project,
                      @NotNull Collection<MavenProject> mavenProjects,
                      @NotNull MavenGeneralSettings generalSettings,
                      @NotNull MavenEmbeddersManager embeddersManager,
                      @NotNull MavenConsole console,
                      @NotNull ResolveContext context,
                      @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    // Tycho requires breaking changes in submitting the correct Maven projects
    // to the IJ Maven server implementation. Separating it seems the most logical choice
    if (generalSettings.isTychoProject()) {
      resolveTycho(project, mavenProjects, generalSettings, embeddersManager, console, context, process);
      return;
    }

    MultiMap<Path, MavenProject> projectMultiMap = groupByBasedir(mavenProjects);

    for (Entry<Path, Collection<MavenProject>> entry : projectMultiMap.entrySet()) {
      String baseDir = entry.getKey().toString();
      MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(FOR_DEPENDENCIES_RESOLVE, baseDir, baseDir);
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

      MavenUtil.restartConfigHighlightning(project, mavenProjects);
    }
  }

  private void resolveTycho(
    @NotNull final Project project,
    @NotNull final Collection<MavenProject> mavenProjects,
    @NotNull final MavenGeneralSettings generalSettings,
    @NotNull final MavenEmbeddersManager embeddersManager,
    @NotNull final MavenConsole console,
    @NotNull final ResolveContext context,
    @NotNull final MavenProgressIndicator process) throws MavenProcessCanceledException {
    final MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
    final List<MavenProject> allMavenProjects = mavenProjectsManager.getProjects();
    final Set<MavenProject> requiredMavenProjects = new HashSet<>(mavenProjects);

    // What we do here is compare the Maven projects that we received as input,
    // with the complete set of local ones. Two situations may occur:
    // 1. a resolve has been requested for a subset of projects (1..n),
    //    in which case we have to first calculate the required local dependencies
    // 2. a resolve has been requested for the entire workspace,
    //    in which case we don't need to do anything as we'll pass in all of them
    if (!mavenProjects.equals(new HashSet<>(allMavenProjects))) {
      process.setText(MavenProjectBundle.message("maven.resolving.tycho"));

      // Let's parse the Maven project's MANIFEST files
      final Map<MavenProject, MavenProjectManifestData> manifestsData =
        StreamEx.of(allMavenProjects)
          .mapToEntry(mavenProject -> {
            try {
              return parseManifest(mavenProject);
            } catch (final IOException e) {
              MavenLog.LOG.error("Could not read MANIFEST file of project " + mavenProject, e);
              return null;
            }
          })
          .nonNullValues()
          .toMap();

      for (final MavenProject mavenProject : mavenProjects) {
        process.checkCanceled();
        collectRequiredMavenProjects(requiredMavenProjects, manifestsData, mavenProject);
      }
    }

    final MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(FOR_DEPENDENCIES_RESOLVE, project.getBasePath(), "");
    final Properties userProperties = new Properties();

    for (MavenProject mavenProject : requiredMavenProjects) {
      mavenProject.setConfigFileError(null);

      for (final MavenImporter mavenImporter : MavenImporter.getSuitableImporters(mavenProject)) {
        mavenImporter.customizeUserProperties(project, mavenProject, userProperties);
      }
    }

    final boolean updateSnapshots = mavenProjectsManager.getForceUpdateSnapshots() || generalSettings.isAlwaysUpdateSnapshots();
    embedder.customizeForResolve(myTree.getWorkspaceMap(), console, process, updateSnapshots, userProperties);

    try {
      doResolve(project, requiredMavenProjects, generalSettings, embedder, context, process);
    } catch (final Throwable t) {
      final MavenConfigParseException cause = findParseException(t);

      if (cause != null) {
        MavenLog.LOG.warn("Cannot parse maven config", cause);
      } else {
        throw t;
      }
    } finally {
      embeddersManager.release(embedder);
    }

    MavenUtil.restartConfigHighlightning(project, requiredMavenProjects);
  }

  @Nullable
  private static MavenProjectManifestData parseManifest(@NotNull final MavenProject mavenProject) throws IOException {
    final VirtualFile directoryFile = mavenProject.getDirectoryFile();
    final VirtualFile manifestFile = directoryFile.findFileByRelativePath("META-INF/MANIFEST.MF");

    if (manifestFile == null) {
      return null;
    }

    final Manifest manifest = new Manifest(manifestFile.getInputStream());
    final Set<String> requiredBundles = new HashSet<>(32);
    final Set<String> importedPackages = new HashSet<>(32);
    final Set<String> exportedPackages = new HashSet<>(32);
    final Attributes manifestAttributes = manifest.getMainAttributes();
    final String requiredBundlesStr = manifestAttributes.getValue("Require-Bundle");

    if (requiredBundlesStr != null) {
      for (final String bundle : requiredBundlesStr.split(",")) {
        // Simply extract the Bundle name, discarding version requirements.
        // Tycho will tell us if we have the right version or not
        requiredBundles.add(bundle.split(";")[0].trim());
      }
    }

    final String importedPackagesStr = manifestAttributes.getValue("Import-Package");

    if (importedPackagesStr != null) {
      for (final String packageName : importedPackagesStr.split(",")) {
        importedPackages.add(packageName.trim());
      }
    }

    final String exportedPackagesStr = manifestAttributes.getValue("Export-Package");

    if (exportedPackagesStr != null) {
      for (final String packageName : exportedPackagesStr.split(",")) {
        exportedPackages.add(packageName.trim());
      }
    }

    return new MavenProjectManifestData(requiredBundles, importedPackages, exportedPackages);
  }

  private static void collectRequiredMavenProjects(
    @NotNull final Set<MavenProject> requiredProjects,
    @NotNull final Map<MavenProject, MavenProjectManifestData> manifestsData,
    @NotNull final MavenProject mavenProject) {
    final MavenProjectManifestData manifestData = manifestsData.get(mavenProject);

    if (manifestData == null) {
      return;
    }

    for (final String requiredBundle : manifestData.requiredBundles()) {
      for (final MavenProject requiredProject : manifestsData.keySet()) {
        if (requiredBundle.equals(requiredProject.getMavenId().getArtifactId())) {
          requiredProjects.add(requiredProject);
          collectRequiredMavenProjects(requiredProjects, manifestsData, requiredProject);
        }
      }
    }

    for (final String importedPackage : manifestData.importedPackages()) {
      for (final Entry<MavenProject, MavenProjectManifestData> projectInfo : manifestsData.entrySet()) {
        for (final String exportedPackage : projectInfo.getValue().exportedPackages()) {
          if (importedPackage.equals(exportedPackage)) {
            final MavenProject requiredProject = projectInfo.getKey();
            requiredProjects.add(requiredProject);
            collectRequiredMavenProjects(requiredProjects, manifestsData, requiredProject);
          }
        }
      }
    }
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

      MavenProject.Snapshot snapshot = mavenProjectCandidate.getSnapshot();
      mavenProjectCandidate
        .set(result, generalSettings, false, MavenProjectReaderResult.shouldResetDependenciesAndFolders(result), false);
      if (result.nativeMavenProject != null) {
        PluginFeatureEnabler.getInstance(myProject).scheduleEnableSuggested();

        for (MavenImporter eachImporter : MavenImporter.getSuitableImporters(mavenProjectCandidate)) {
          eachImporter.resolve(project, mavenProjectCandidate, result.nativeMavenProject, embedder, context);
        }
      }
      // project may be modified by MavenImporters, so we need to collect the changes after them:
      MavenProjectChanges changes = mavenProjectCandidate.getChangesSinceSnapshot(snapshot);

      mavenProjectCandidate.getProblems(); // need for fill problem cache
      myTree.fireProjectResolved(Pair.create(mavenProjectCandidate, changes), result.nativeMavenProject);
    }
  }

  public Set<MavenPlugin> resolvePlugins(@NotNull MavenProject mavenProject,
                                         @NotNull NativeMavenProjectHolder nativeMavenProject,
                                         @NotNull MavenEmbeddersManager embeddersManager,
                                         @NotNull MavenConsole console,
                                         @NotNull MavenProgressIndicator process,
                                         boolean reportUnresolvedToSyncConsole,
                                         boolean forceUpdateSnapshots) throws MavenProcessCanceledException {
    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(mavenProject, MavenEmbeddersManager.FOR_PLUGINS_RESOLVE);
    embedder.customizeForResolve(console, process, forceUpdateSnapshots);
    embedder.clearCachesFor(mavenProject.getMavenId());

    Set<Path> filesToRefresh = new HashSet<>();
    Set<MavenPlugin> unresolvedPlugins = new HashSet<>();
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
    for (Entry<Path, Collection<MavenProject>> entry : projectMultiMap.entrySet()) {
      String baseDir = entry.getKey().toString();
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
