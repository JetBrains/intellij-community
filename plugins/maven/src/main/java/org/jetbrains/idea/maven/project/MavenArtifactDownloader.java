// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.MavenArtifactResolutionRequest;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class MavenArtifactDownloader {
  private final Project myProject;
  private final MavenProjectsTree myProjectsTree;
  private final Collection<MavenArtifact> myArtifacts;
  private final MavenProgressIndicator myProgress;

  public static DownloadResult download(@NotNull Project project,
                                        MavenProjectsTree projectsTree,
                                        Collection<MavenProject> mavenProjects,
                                        @Nullable Collection<MavenArtifact> artifacts,
                                        boolean downloadSources,
                                        boolean downloadDocs,
                                        MavenEmbedderWrapper embedder,
                                        MavenProgressIndicator p) throws MavenProcessCanceledException {
    return new MavenArtifactDownloader(project, projectsTree, artifacts, p)
      .download(mavenProjects, embedder, downloadSources, downloadDocs);
  }

  public MavenArtifactDownloader(@NotNull Project project,
                                 MavenProjectsTree projectsTree,
                                 Collection<MavenArtifact> artifacts,
                                 MavenProgressIndicator progressIndicator) {
    myProject = project;
    myProjectsTree = projectsTree;
    myArtifacts = artifacts == null ? null : new HashSet<>(artifacts);
    myProgress = progressIndicator;
  }

  public @NotNull DownloadResult downloadSourcesAndJavadocs(Collection<MavenProject> mavenProjects,
                                                            boolean downloadSources,
                                                            boolean downloadDocs,
                                                            @NotNull MavenEmbeddersManager embeddersManager,
                                                            @NotNull MavenConsole console)
    throws MavenProcessCanceledException {
    var projectMultiMap = MavenUtil.groupByBasedir(mavenProjects, myProjectsTree);
    DownloadResult result = new DownloadResult();
    for (var entry : projectMultiMap.entrySet()) {
      var baseDir = entry.getKey();
      var mavenProjectsForBaseDir = entry.getValue();
      var embedder = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_DOWNLOAD, baseDir);
      try {
        embedder.customizeForResolve(console, myProgress, false, null, null);
        var chunk = download(mavenProjectsForBaseDir, embedder, downloadSources, downloadDocs);

        for (MavenProject each : mavenProjectsForBaseDir) {
          myProjectsTree.fireArtifactsDownloaded(each);
        }

        result.resolvedDocs.addAll(chunk.resolvedDocs);
        result.resolvedSources.addAll(chunk.resolvedSources);
        result.unresolvedDocs.addAll(chunk.unresolvedDocs);
        result.unresolvedSources.addAll(chunk.unresolvedSources);
      }
      finally {
        embeddersManager.release(embedder);
      }
    }
    return result;
  }

  private DownloadResult download(Collection<MavenProject> mavenProjects,
                                  MavenEmbedderWrapper embedder,
                                  boolean downloadSources,
                                  boolean downloadDocs)
    throws MavenProcessCanceledException {
    Collection<File> downloadedFiles = new ConcurrentLinkedQueue<>();
    try {
      List<MavenExtraArtifactType> types = new ArrayList<>(2);
      if (downloadSources) types.add(MavenExtraArtifactType.SOURCES);
      if (downloadDocs) types.add(MavenExtraArtifactType.DOCS);

      String caption = downloadSources && downloadDocs
                       ? MavenProjectBundle.message("maven.downloading")
                       : (downloadSources
                          ? MavenProjectBundle.message("maven.downloading.sources")
                          : MavenProjectBundle.message("maven.downloading.docs"));
      myProgress.setText(caption);

      Map<MavenId, DownloadData> artifacts = collectArtifactsToDownload(mavenProjects, types);
      return download(embedder, artifacts, downloadedFiles);
    }
    finally {
      boolean isAsync = !MavenUtil.isMavenUnitTestModeEnabled();

      // We have to refresh parents of downloaded files, because some additional files may have been downloaded
      Set<File> filesToRefresh = new HashSet<>();
      for (File file : downloadedFiles) {
        filesToRefresh.add(file);
        filesToRefresh.add(file.getParentFile());
      }

      LocalFileSystem.getInstance().refreshIoFiles(filesToRefresh, isAsync, false, null);
    }
  }

  private Map<MavenId, DownloadData> collectArtifactsToDownload(Collection<MavenProject> mavenProjects,
                                                                List<MavenExtraArtifactType> types) {
    Map<MavenId, DownloadData> result = new HashMap<>();

    Set<String> dependencyTypesFromSettings = new HashSet<>();

    if (!ReadAction.compute(() -> {
      if (myProject.isDisposed()) return false;
      dependencyTypesFromSettings.addAll(MavenProjectsManager.getInstance(myProject).getImportingSettings().getDependencyTypesAsSet());
      return true;
    })) {
      return result;
    }

    for (MavenProject eachProject : mavenProjects) {
      List<MavenRemoteRepository> repositories = eachProject.getRemoteRepositories();

      for (MavenArtifact eachDependency : eachProject.getDependencies()) {
        if (myArtifacts != null && !myArtifacts.contains(eachDependency)) continue;

        if (MavenConstants.SCOPE_SYSTEM.equalsIgnoreCase(eachDependency.getScope())) continue;
        if (myProjectsTree.findProject(eachDependency.getMavenId()) != null) continue;

        String dependencyType = eachDependency.getType();

        if (!dependencyTypesFromSettings.contains(dependencyType)
            && !eachProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT).contains(dependencyType)) {
          continue;
        }

        MavenId id = eachDependency.getMavenId();
        DownloadData data = result.get(id);
        if (data == null) {
          data = new DownloadData();
          result.put(id, data);
        }
        data.repositories.addAll(repositories);
        for (MavenExtraArtifactType eachType : types) {
          Pair<String, String> classifierAndExtension = eachProject.getClassifierAndExtension(eachDependency, eachType);
          String classifier = eachDependency.getFullClassifier(classifierAndExtension.first);
          String extension = classifierAndExtension.second;
          data.classifiersWithExtensions.add(new DownloadElement(classifier, extension, eachType));
        }
      }
    }
    return result;
  }

  private DownloadResult download(MavenEmbedderWrapper embedder,
                                  Map<MavenId, DownloadData> toDownload,
                                  Collection<File> downloadedFiles) throws MavenProcessCanceledException {
    DownloadResult result = new DownloadResult();
    result.unresolvedSources.addAll(toDownload.keySet());
    result.unresolvedDocs.addAll(toDownload.keySet());

    var requests = new ArrayList<MavenArtifactResolutionRequest>();
    for (Map.Entry<MavenId, DownloadData> eachEntry : toDownload.entrySet()) {
      myProgress.checkCanceled();

      DownloadData data = eachEntry.getValue();
      MavenId id = eachEntry.getKey();

      for (DownloadElement eachElement : data.classifiersWithExtensions) {
        MavenArtifactInfo info = new MavenArtifactInfo(id, eachElement.extension, eachElement.classifier);
        var request = new MavenArtifactResolutionRequest(info, new ArrayList<>(data.repositories));
        requests.add(request);
      }
    }

    var artifacts = embedder.resolve(requests, myProgress);
    for (var artifact : artifacts) {
      File file = artifact.getFile();
      if (file.exists()) {
        downloadedFiles.add(file);

        var mavenId = new MavenId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        if (MavenExtraArtifactType.SOURCES.getDefaultClassifier().equals(artifact.getClassifier())) {
          result.resolvedSources.add(mavenId);
          result.unresolvedSources.remove(mavenId);
        }
        else {
          result.resolvedDocs.add(mavenId);
          result.unresolvedDocs.remove(mavenId);
        }
      }
    }

    return result;
  }

  private static class DownloadData {
    public final LinkedHashSet<MavenRemoteRepository> repositories = new LinkedHashSet<>();
    public final LinkedHashSet<DownloadElement> classifiersWithExtensions = new LinkedHashSet<>();
  }

  private static class DownloadElement {
    public final String classifier;
    public final String extension;
    public final MavenExtraArtifactType type;

    DownloadElement(String classifier, String extension, MavenExtraArtifactType type) {
      this.classifier = classifier;
      this.extension = extension;
      this.type = type;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      DownloadElement that = (DownloadElement)o;

      if (classifier != null ? !classifier.equals(that.classifier) : that.classifier != null) return false;
      if (extension != null ? !extension.equals(that.extension) : that.extension != null) return false;
      if (type != that.type) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = classifier != null ? classifier.hashCode() : 0;
      result = 31 * result + (extension != null ? extension.hashCode() : 0);
      result = 31 * result + (type != null ? type.hashCode() : 0);
      return result;
    }
  }

  // used by third-party plugins
  public static class DownloadResult {
    public final Set<MavenId> resolvedSources = ConcurrentHashMap.newKeySet();
    public final Set<MavenId> resolvedDocs = ConcurrentHashMap.newKeySet();

    public final Set<MavenId> unresolvedSources = ConcurrentHashMap.newKeySet();
    public final Set<MavenId> unresolvedDocs = ConcurrentHashMap.newKeySet();
  }
}
