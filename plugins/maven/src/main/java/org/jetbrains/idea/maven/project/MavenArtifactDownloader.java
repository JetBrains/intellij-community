/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MavenArtifactDownloader {
  private final static ThreadPoolExecutor EXECUTOR =
    new ThreadPoolExecutor(5, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

  private final MavenProjectsTree myProjectsTree;
  private final Collection<MavenProject> myMavenProjects;
  private final Collection<MavenArtifact> myArtifacts;
  private final MavenProgressIndicator myProgress;
  private final MavenEmbedderWrapper myEmbedder;

  public static DownloadResult download(MavenProjectsTree projectsTree,
                                        Collection<MavenProject> mavenProjects,
                                        @Nullable Collection<MavenArtifact> artifacts,
                                        boolean downloadSources,
                                        boolean downloadDocs,
                                        MavenEmbedderWrapper embedder,
                                        MavenProgressIndicator p) throws MavenProcessCanceledException {
    return new MavenArtifactDownloader(projectsTree, mavenProjects, artifacts, embedder, p).download(downloadSources, downloadDocs);
  }

  private MavenArtifactDownloader(MavenProjectsTree projectsTree,
                                  Collection<MavenProject> mavenProjects,
                                  Collection<MavenArtifact> artifacts,
                                  MavenEmbedderWrapper embedder,
                                  MavenProgressIndicator p) {
    myProjectsTree = projectsTree;
    myMavenProjects = mavenProjects;
    myArtifacts = artifacts == null ? null : new THashSet<MavenArtifact>(artifacts);
    myEmbedder = embedder;
    myProgress = p;
  }

  private DownloadResult download(boolean downloadSources, boolean downloadDocs) throws MavenProcessCanceledException {
    List<File> downloadedFiles = new ArrayList<File>();
    try {
      List<MavenExtraArtifactType> types = new ArrayList<MavenExtraArtifactType>(2);
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
      scheduleFilesRefresh(downloadedFiles);
    }
  }

  private void scheduleFilesRefresh(final List<File> downloadedFiles) {
    Runnable refreshTask = new Runnable() {
      public void run() {
        LocalFileSystem.getInstance().refreshIoFiles(downloadedFiles);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()
        || ApplicationManager.getApplication().isDispatchThread()) {
      refreshTask.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(refreshTask);
    }
  }

  private Map<MavenId, DownloadData> collectArtifactsToDownload(List<MavenExtraArtifactType> types) {
    Map<MavenId, DownloadData> result = new THashMap<MavenId, DownloadData>();

    for (MavenProject eachProject : myMavenProjects) {
      List<MavenRemoteRepository> repositories = eachProject.getRemoteRepositories();

      for (MavenArtifact eachDependency : eachProject.getDependencies()) {
        if (myArtifacts != null && !myArtifacts.contains(eachDependency)) continue;

        if (MavenConstants.SCOPE_SYSTEM.equalsIgnoreCase(eachDependency.getScope())) continue;
        if (myProjectsTree.findProject(eachDependency.getMavenId()) != null) continue;
        if (!eachProject.isSupportedDependency(eachDependency)) continue;

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
    List<Future> futures = new ArrayList<Future>();

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
          futures.add(EXECUTOR.submit(new Runnable() {
            public void run() {
              try {
                myProgress.checkCanceled();
                myProgress.setFraction(((double)downloaded.getAndIncrement()) / finalTotal);

                MavenArtifact a = myEmbedder.resolve(new MavenArtifactInfo(id, eachElement.extension, eachElement.classifier),
                                                     new ArrayList<MavenRemoteRepository>(data.repositories));
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
    public final LinkedHashSet<MavenRemoteRepository> repositories = new LinkedHashSet<MavenRemoteRepository>();
    public final LinkedHashSet<DownloadElement> classifiersWithExtensions = new LinkedHashSet<DownloadElement>();
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
    public final Set<MavenId> resolvedSources = new THashSet<MavenId>();
    public final Set<MavenId> resolvedDocs = new THashSet<MavenId>();

    public final Set<MavenId> unresolvedSources = new THashSet<MavenId>();
    public final Set<MavenId> unresolvedDocs = new THashSet<MavenId>();
  }
}
