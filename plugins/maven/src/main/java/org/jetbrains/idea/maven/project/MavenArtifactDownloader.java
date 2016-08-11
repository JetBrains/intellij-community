/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.ConcurrencyUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MavenArtifactDownloader {
  private static final ThreadPoolExecutor EXECUTOR =
    new ThreadPoolExecutor(0, Integer.MAX_VALUE, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadFactory() {
      private final AtomicInteger num = new AtomicInteger();

      @NotNull
      @Override
      public Thread newThread(@NotNull Runnable r) {
        return new Thread(r, "Maven Artifact Downloader " + num.getAndIncrement());
      }
    });

  private final Project myProject;
  private final MavenProjectsTree myProjectsTree;
  private final Collection<MavenProject> myMavenProjects;
  private final Collection<MavenArtifact> myArtifacts;
  private final MavenProgressIndicator myProgress;
  private final MavenEmbedderWrapper myEmbedder;

  public static DownloadResult download(@NotNull Project project,
                                        MavenProjectsTree projectsTree,
                                        Collection<MavenProject> mavenProjects,
                                        @Nullable Collection<MavenArtifact> artifacts,
                                        boolean downloadSources,
                                        boolean downloadDocs,
                                        MavenEmbedderWrapper embedder,
                                        MavenProgressIndicator p) throws MavenProcessCanceledException {
    return new MavenArtifactDownloader(project, projectsTree, mavenProjects, artifacts, embedder, p).download(downloadSources, downloadDocs);
  }

  private MavenArtifactDownloader(@NotNull Project project,
                                  MavenProjectsTree projectsTree,
                                  Collection<MavenProject> mavenProjects,
                                  Collection<MavenArtifact> artifacts,
                                  MavenEmbedderWrapper embedder,
                                  MavenProgressIndicator p) {
    myProject = project;
    myProjectsTree = projectsTree;
    myMavenProjects = mavenProjects;
    myArtifacts = artifacts == null ? null : new THashSet<>(artifacts);
    myEmbedder = embedder;
    myProgress = p;
  }

  private DownloadResult download(boolean downloadSources, boolean downloadDocs) throws MavenProcessCanceledException {
    List<File> downloadedFiles = new ArrayList<>();
    try {
      List<MavenExtraArtifactType> types = new ArrayList<>(2);
      if (downloadSources) types.add(MavenExtraArtifactType.SOURCES);
      if (downloadDocs) types.add(MavenExtraArtifactType.DOCS);

      String caption = downloadSources && downloadDocs
                       ? ProjectBundle.message("maven.downloading")
                       : (downloadSources
                          ? ProjectBundle.message("maven.downloading.sources")
                          : ProjectBundle.message("maven.downloading.docs"));
      myProgress.setText(caption);

      Map<MavenId, DownloadData> artifacts = collectArtifactsToDownload(types);
      return download(artifacts, downloadedFiles);
    }
    finally {
      boolean isAsync = !ApplicationManager.getApplication().isUnitTestMode();

      Set<File> parentsToRefresh = new HashSet<>(); // We have to refresh parents of downloaded files, because some additional files  may have been download.
      for (File file : downloadedFiles) {
        parentsToRefresh.add(file.getParentFile());
      }

      LocalFileSystem.getInstance().refreshIoFiles(parentsToRefresh, isAsync, false, null);
    }
  }

  private Map<MavenId, DownloadData> collectArtifactsToDownload(List<MavenExtraArtifactType> types) {
    Map<MavenId, DownloadData> result = new THashMap<>();

    THashSet<String> dependencyTypesFromSettings = new THashSet<>();

    AccessToken accessToken = ReadAction.start();
    try {
      if (myProject.isDisposed()) return result;

      dependencyTypesFromSettings.addAll(MavenProjectsManager.getInstance(myProject).getImportingSettings().getDependencyTypesAsSet());
    }
    finally {
      accessToken.finish();
    }

    for (MavenProject eachProject : myMavenProjects) {
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

  private DownloadResult download(final Map<MavenId, DownloadData> toDownload,
                                  final List<File> downloadedFiles) throws MavenProcessCanceledException {
    List<Future> futures = new ArrayList<>();

    final AtomicInteger downloaded = new AtomicInteger();
    int total = 0;
    for (DownloadData each : toDownload.values()) {
      total += each.classifiersWithExtensions.size();
    }

    final DownloadResult result = new DownloadResult();
    result.unresolvedSources.addAll(toDownload.keySet());
    result.unresolvedDocs.addAll(toDownload.keySet());

    try {
      for (final Map.Entry<MavenId, DownloadData> eachEntry : toDownload.entrySet()) {
        myProgress.checkCanceled();

        final DownloadData data = eachEntry.getValue();
        final MavenId id = eachEntry.getKey();

        for (final DownloadElement eachElement : data.classifiersWithExtensions) {
          final int finalTotal = total;
          futures.add(EXECUTOR.submit(() -> {
            try {
              if (myProject.isDisposed()) return;

              myProgress.checkCanceled();
              myProgress.setFraction(((double)downloaded.getAndIncrement()) / finalTotal);

              MavenArtifact a = myEmbedder.resolve(new MavenArtifactInfo(id, eachElement.extension, eachElement.classifier),
                                                   new ArrayList<>(data.repositories));
              File file = a.getFile();
              if (file.exists()) {
                synchronized (downloadedFiles) {
                  downloadedFiles.add(file);

                  switch (eachElement.type) {
                    case SOURCES:
                      result.resolvedSources.add(id);
                      result.unresolvedSources.remove(id);
                      break;
                    case DOCS:
                      result.resolvedDocs.add(id);
                      result.unresolvedDocs.remove(id);
                      break;
                  }
                }
              }
            }
            catch (MavenProcessCanceledException ignore) {
            }
          }));
        }
      }
    }
    finally {
      for (Future each : futures) {
        try {
          each.get();
        }
        catch (Exception e) {
          MavenLog.LOG.error(e);
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

    public DownloadElement(String classifier, String extension, MavenExtraArtifactType type) {
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

  public static class DownloadResult {
    public final Set<MavenId> resolvedSources = new THashSet<>();
    public final Set<MavenId> resolvedDocs = new THashSet<>();

    public final Set<MavenId> unresolvedSources = new THashSet<>();
    public final Set<MavenId> unresolvedDocs = new THashSet<>();
  }

  @TestOnly
  public static void awaitQuiescence(long timeout, @NotNull TimeUnit unit) {
    ConcurrencyUtil.awaitQuiescence(EXECUTOR, timeout, unit);
  }
}
