// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.issue.BuildIssue;
import com.intellij.ide.plugins.advertiser.PluginFeatureEnabler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes.MavenConfigBuildIssue;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.server.MavenConfigParseException;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.*;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

class MavenProjectResolverImpl implements MavenProjectResolver {
  @NotNull private final Project myProject;

  MavenProjectResolverImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public MavenProjectResolutionResult resolve(@NotNull Collection<MavenProject> mavenProjects,
                                              @NotNull MavenProjectsTree tree,
                                              @NotNull MavenGeneralSettings generalSettings,
                                              @NotNull MavenEmbeddersManager embeddersManager,
                                              @NotNull MavenConsole console,
                                              @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    boolean updateSnapshots = MavenProjectsManager.getInstance(myProject).getForceUpdateSnapshots() || generalSettings.isAlwaysUpdateSnapshots();
    var projectsWithUnresolvedPlugins = new HashMap<String, Collection<MavenProjectWithHolder>>();
    MultiMap<String, MavenProject> projectMultiMap = MavenUtil.groupByBasedir(mavenProjects, tree);

    for (var entry : projectMultiMap.entrySet()) {
      String baseDir = entry.getKey();
      var mavenProjectsInBaseDir = entry.getValue();
      MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE, baseDir);
      try {
        for (MavenProject mavenProject : mavenProjectsInBaseDir) {
          mavenProject.setConfigFileError(null);
        }
        embedder.customizeForResolve(updateSnapshots, tree.getWorkspaceMap());
        embedder.startPullingProgress(console, process);
        var projectsWithUnresolvedPluginsChunk = doResolve(mavenProjectsInBaseDir, tree, generalSettings, embedder, process);
        projectsWithUnresolvedPlugins.put(baseDir, projectsWithUnresolvedPluginsChunk);
      }
      catch (Throwable t) {
        MavenConfigParseException cause = findParseException(t);
        if (cause != null) {
          BuildIssue buildIssue = MavenConfigBuildIssue.INSTANCE.getIssue(cause);
          if (buildIssue != null) {
            MavenProjectsManager.getInstance(myProject).getSyncConsole().addBuildIssue(buildIssue, MessageEvent.Kind.ERROR);
          }
          else {
            throw t;
          }
        }
        else {
          MavenLog.LOG.warn("Error in maven config parsing", t);
          throw t;
        }
      }
      finally {
        embeddersManager.release(embedder);
      }
    }

    MavenUtil.restartConfigHighlighting(mavenProjects);

    return new MavenProjectResolutionResult(projectsWithUnresolvedPlugins);
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

  private Collection<MavenProjectWithHolder> doResolve(@NotNull Collection<MavenProject> mavenProjects,
                                                       @NotNull MavenProjectsTree tree,
                                                       @NotNull MavenGeneralSettings generalSettings,
                                                       @NotNull MavenEmbedderWrapper embedder,
                                                       @NotNull MavenProgressIndicator process)
    throws MavenProcessCanceledException {
    if (mavenProjects.isEmpty()) return List.of();

    process.checkCanceled();
    final List<String> names = ContainerUtil.mapNotNull(mavenProjects, p -> p.getDisplayName());
    final String text = StringUtil.shortenPathWithEllipsis(StringUtil.join(names, ", "), 200);
    process.setText(MavenProjectBundle.message("maven.resolving.pom", text));
    process.setText2("");

    MavenExplicitProfiles explicitProfiles = tree.getExplicitProfiles();
    Collection<VirtualFile> files = ContainerUtil.map(mavenProjects, p -> p.getFile());
    Collection<MavenProjectReaderResult> results = new MavenProjectReader(myProject)
      .resolveProject(generalSettings, embedder, files, explicitProfiles, tree.getProjectLocator(), process);

    MavenResolveResultProblemProcessor.MavenResolveProblemHolder problems = MavenResolveResultProblemProcessor.getProblems(results);
    MavenResolveResultProblemProcessor.notifySyncForProblem(myProject, problems);

    var artifactIdToMavenProjects = mavenProjects.stream()
      .filter(mavenProject -> null != mavenProject.getMavenId().getArtifactId())
      .collect(Collectors.groupingBy(mavenProject -> mavenProject.getMavenId().getArtifactId()));
    var projectsWithUnresolvedPlugins = new ConcurrentLinkedQueue<MavenProjectWithHolder>();
    ParallelRunner.<MavenProjectReaderResult, MavenProcessCanceledException>runInParallelRethrow(results, result -> {
      doResolve(result, artifactIdToMavenProjects, generalSettings, embedder, tree, projectsWithUnresolvedPlugins);
    });

    return projectsWithUnresolvedPlugins;
  }

  private void doResolve(@NotNull MavenProjectReaderResult result,
                         @NotNull Map<String, List<MavenProject>> artifactIdToMavenProjects,
                         @NotNull MavenGeneralSettings generalSettings,
                         @NotNull MavenEmbedderWrapper embedder,
                         @NotNull MavenProjectsTree tree,
                         @NotNull ConcurrentLinkedQueue<MavenProjectWithHolder> projectsWithUnresolvedPlugins)
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
    var resetArtifacts = MavenUtil.shouldResetDependenciesAndFolders(result.readingProblems);
    mavenProjectCandidate.set(result, generalSettings, false, resetArtifacts, false);
    NativeMavenProjectHolder nativeMavenProject = result.nativeMavenProject;
    if (nativeMavenProject != null) {
      PluginFeatureEnabler.getInstance(myProject).scheduleEnableSuggested();

      for (MavenImporter eachImporter : MavenImporter.getSuitableImporters(mavenProjectCandidate)) {
        eachImporter.resolve(myProject, mavenProjectCandidate, nativeMavenProject, embedder);
      }
    }
    // project may be modified by MavenImporters, so we need to collect the changes after them:
    MavenProjectChanges changes = mavenProjectCandidate.getChangesSinceSnapshot(snapshot);

    mavenProjectCandidate.getProblems(); // need for fill problem cache
    tree.fireProjectResolved(Pair.create(mavenProjectCandidate, changes), nativeMavenProject);

    if (null != nativeMavenProject) {
      if (!mavenProjectCandidate.hasReadingProblems() && mavenProjectCandidate.hasUnresolvedPlugins()) {
        projectsWithUnresolvedPlugins.add(new MavenProjectWithHolder(mavenProjectCandidate, nativeMavenProject));
      }
    }
  }
}

