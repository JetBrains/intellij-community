// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MavenPluginResolver {
  private final MavenProjectsTree myTree;
  private final Project myProject;

  public MavenPluginResolver(@Nullable MavenProjectsTree tree) {
    myTree = tree;
    myProject = tree == null ? null : tree.getProject();
  }

  public void resolvePlugins(@NotNull Collection<MavenProjectWithHolder> mavenProjects,
                             @NotNull MavenEmbeddersManager embeddersManager,
                             @NotNull MavenConsole console,
                             @NotNull MavenProgressIndicator process,
                             boolean reportUnresolvedToSyncConsole,
                             boolean forceUpdateSnapshots) throws MavenProcessCanceledException {
    if (mavenProjects.isEmpty()) return;

    var firstProject = sortAndGetFirst(mavenProjects).mavenProject();
    var baseDir = MavenUtil.getBaseDir(firstProject.getDirectoryFile()).toString();
    process.setText(MavenProjectBundle.message("maven.downloading.pom.plugins", firstProject.getDisplayName()));

    MavenEmbedderWrapper embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_PLUGINS_RESOLVE, baseDir);
    embedder.startPullingProgress(console, process);

    Set<MavenId> unresolvedPluginIds;
    Set<Path> filesToRefresh = new HashSet<>();

    try {
      var mavenPluginIdsToResolve = collectMavenPluginIdsToResolve(mavenProjects);
      var resolutionResults = embedder.resolvePlugins(mavenPluginIdsToResolve);
      var artifacts = resolutionResults.stream()
        .flatMap(resolutionResult -> resolutionResult.getArtifacts().stream())
        .collect(Collectors.toSet());

      for (MavenArtifact artifact : artifacts) {
        Path pluginJar = artifact.getFile().toPath();
        Path pluginDir = pluginJar.getParent();
        if (pluginDir != null) {
          filesToRefresh.add(pluginDir); // Refresh both *.pom and *.jar files.
        }
      }

      unresolvedPluginIds = resolutionResults.stream()
        .filter(resolutionResult -> !resolutionResult.isResolved())
        .map(resolutionResult -> resolutionResult.getMavenPluginId())
        .collect(Collectors.toSet());

      if (reportUnresolvedToSyncConsole && myProject != null) {
        reportUnresolvedPlugins(unresolvedPluginIds);
      }

      var updatedMavenProjects = mavenProjects.stream().map(p -> p.mavenProject()).collect(Collectors.toSet());
      for (var mavenProject : updatedMavenProjects) {
        mavenProject.resetCache();
        myTree.firePluginsResolved(mavenProject);
      }
    }
    finally {
      if (filesToRefresh.size() > 0) {
        LocalFileSystem.getInstance().refreshNioFiles(filesToRefresh);
      }
      embeddersManager.release(embedder);
    }
  }

  private static Collection<Pair<MavenId, NativeMavenProjectHolder>> collectMavenPluginIdsToResolve(
    @NotNull Collection<MavenProjectWithHolder> mavenProjects
  ) {
    var mavenPluginIdsToResolve = new HashSet<Pair<MavenId, NativeMavenProjectHolder>>();

    if (Registry.is("maven.plugins.use.cache")) {
      var pluginIdsToProjects = new HashMap<MavenId, List<MavenProjectWithHolder>>();
      for (var projectData : mavenProjects) {
        var mavenProject = projectData.mavenProject();
        for (MavenPlugin mavenPlugin : mavenProject.getDeclaredPlugins()) {
          var mavenPluginId = mavenPlugin.getMavenId();
          pluginIdsToProjects.putIfAbsent(mavenPluginId, new ArrayList<>());
          pluginIdsToProjects.get(mavenPluginId).add(projectData);
        }
      }
      for (var entry : pluginIdsToProjects.entrySet()) {
        mavenPluginIdsToResolve.add(Pair.create(entry.getKey(), sortAndGetFirst(entry.getValue()).mavenProjectHolder()));
      }
    }
    else {
      for (var projectData : mavenProjects) {
        var mavenProject = projectData.mavenProject();
        var nativeMavenProject = projectData.mavenProjectHolder();
        for (MavenPlugin mavenPlugin : mavenProject.getDeclaredPlugins()) {
          mavenPluginIdsToResolve.add(Pair.create(mavenPlugin.getMavenId(), nativeMavenProject));
        }
      }
    }
    return mavenPluginIdsToResolve;
  }

  private void reportUnresolvedPlugins(Set<MavenId> unresolvedPluginIds) {
    if (!unresolvedPluginIds.isEmpty()) {
      for (var mavenPluginId : unresolvedPluginIds) {
        MavenProjectsManager.getInstance(myProject)
          .getSyncConsole().getListener(MavenServerProgressIndicator.ResolveType.PLUGIN)
          .showArtifactBuildIssue(mavenPluginId.getKey(), null);
      }
    }
  }

  private static MavenProjectWithHolder sortAndGetFirst(@NotNull Collection<MavenProjectWithHolder> mavenProjects) {
    return mavenProjects.stream()
      .min(Comparator.comparing(p -> p.mavenProject().getDirectoryFile().getPath()))
      .orElse(null);
  }
}
