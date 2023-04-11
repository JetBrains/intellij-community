// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.ide.plugins.advertiser.PluginFeatureEnabler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.server.MavenConfigParseException;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.*;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class MavenProjectResolver {
  public static final Key<Collection<MavenArtifact>> UNRESOLVED_ARTIFACTS = new Key<>("Unresolved Artifacts");

  public MavenProjectResolver() {
  }

  public void resolve(@NotNull Project project,
                      @NotNull MavenProjectsTree tree,
                      @NotNull Collection<MavenProject> mavenProjects,
                      @NotNull MavenGeneralSettings generalSettings,
                      @NotNull MavenEmbeddersManager embeddersManager,
                      @NotNull MavenConsole console,
                      @NotNull ResolveContext context,
                      @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    MultiMap<Path, MavenProject> projectMultiMap = groupByBasedir(mavenProjects, tree);

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
        embedder.customizeForResolve(console, process, updateSnapshots, tree.getWorkspaceMap(), userProperties);
        doResolve(project, tree, entry.getValue(), generalSettings, embedder, context, process);
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
                         @NotNull MavenProjectsTree tree,
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

    MavenExplicitProfiles explicitProfiles = tree.getExplicitProfiles();
    Collection<VirtualFile> files = ContainerUtil.map(mavenProjects, p -> p.getFile());
    Collection<MavenProjectReaderResult> results = new MavenProjectReader(project)
      .resolveProject(generalSettings, embedder, files, explicitProfiles, tree.getProjectLocator());

    MavenResolveResultProblemProcessor.MavenResolveProblemHolder problems = MavenResolveResultProblemProcessor.getProblems(results);
    MavenResolveResultProblemProcessor.notifySyncForProblem(project, problems);

    context.putUserData(UNRESOLVED_ARTIFACTS, problems.unresolvedArtifacts);

    var artifactIdToMavenProjects = mavenProjects.stream()
      .filter(mavenProject -> null != mavenProject.getMavenId().getArtifactId())
      .collect(Collectors.groupingBy(mavenProject -> mavenProject.getMavenId().getArtifactId()));
    var pluginResolutionRequests = new ConcurrentLinkedQueue<Pair<MavenProject, NativeMavenProjectHolder>>();
    ParallelRunner.<MavenProjectReaderResult, MavenProcessCanceledException>runInParallelRethrow(results, result -> {
      doResolve(project, tree, result, artifactIdToMavenProjects, generalSettings, embedder, context, pluginResolutionRequests);
    });
    schedulePluginResolution(project, tree, pluginResolutionRequests);
  }

  private static void doResolve(@NotNull Project project,
                                @NotNull MavenProjectsTree tree,
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
      PluginFeatureEnabler.getInstance(project).scheduleEnableSuggested();

      for (MavenImporter eachImporter : MavenImporter.getSuitableImporters(mavenProjectCandidate)) {
        eachImporter.resolve(project, mavenProjectCandidate, nativeMavenProject, embedder, context);
      }
    }
    // project may be modified by MavenImporters, so we need to collect the changes after them:
    MavenProjectChanges changes = mavenProjectCandidate.getChangesSinceSnapshot(snapshot);

    mavenProjectCandidate.getProblems(); // need for fill problem cache
    tree.fireProjectResolved(Pair.create(mavenProjectCandidate, changes), nativeMavenProject);

    if (null != nativeMavenProject) {
      if (!mavenProjectCandidate.hasReadingProblems() && mavenProjectCandidate.hasUnresolvedPlugins()) {
        pluginResolutionRequests.add(Pair.create(mavenProjectCandidate, nativeMavenProject));
      }
    }
  }

  private void schedulePluginResolution(
    @NotNull Project project,
    @NotNull MavenProjectsTree tree,
    @NotNull Collection<Pair<MavenProject, NativeMavenProjectHolder>> pluginResolutionRequests
  ) {
    var projectsManager = MavenProjectsManager.getInstance(project);
    var task = new MavenProjectsProcessorPluginsResolvingTask(pluginResolutionRequests, new MavenPluginResolver(tree));
    projectsManager.schedulePluginResolution(task);
  }

  @NotNull
  private static MultiMap<Path, MavenProject> groupByBasedir(@NotNull Collection<MavenProject> projects, @NotNull MavenProjectsTree tree) {
    return ContainerUtil.groupBy(projects, p -> MavenUtil.getBaseDir(tree.findRootProject(p).getDirectoryFile()));
  }
}
