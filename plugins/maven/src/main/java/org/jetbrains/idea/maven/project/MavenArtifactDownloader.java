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
import org.apache.maven.artifact.Artifact;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MavenArtifactDownloader {
  private final static ThreadPoolExecutor EXECUTOR =
    new ThreadPoolExecutor(5, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

  private final MavenEmbedderWrapper myEmbedder;
  private final MavenProgressIndicator myProgress;
  private final MavenProjectsTree myProjectsTree;
  private final List<MavenProject> myMavenProjects;

  public static void download(MavenProjectsTree projectsTree,
                              List<MavenProject> mavenProjects,
                              boolean downloadSources,
                              boolean downloadDocs, MavenEmbedderWrapper embedder,
                              MavenProgressIndicator p) throws MavenProcessCanceledException {
    new MavenArtifactDownloader(projectsTree, mavenProjects, embedder, p).download(downloadSources, downloadDocs);
  }

  private MavenArtifactDownloader(MavenProjectsTree projectsTree,
                                  List<MavenProject> mavenProjects,
                                  MavenEmbedderWrapper embedder,
                                  MavenProgressIndicator p) {
    myProjectsTree = projectsTree;
    myMavenProjects = mavenProjects;
    myEmbedder = embedder;
    myProgress = p;
  }

  private void download(boolean downloadSources, boolean downloadDocs) throws MavenProcessCanceledException {
    List<File> downloadedFiles = new ArrayList<File>();
    try {
      List<MavenExtraArtifactType> types = new ArrayList<MavenExtraArtifactType>(2);
      if (downloadSources) types.add(MavenExtraArtifactType.SOURCES);
      if (downloadDocs) types.add(MavenExtraArtifactType.DOCS);

      Map<MavenId, DownloadData> artifacts = collectArtifactsToDownload(types);
      download(artifacts, downloadedFiles);
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
        if (Artifact.SCOPE_SYSTEM.equalsIgnoreCase(eachDependency.getScope())) continue;
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
          data.classifiersWithExtensions.add(eachProject.getClassifierAndExtension(eachDependency, eachType));
        }
      }
    }
    return result;
  }

  private void download(final Map<MavenId, DownloadData> toDownload,
                        final List<File> downloadedFiles) throws MavenProcessCanceledException {
    List<Future> futures = new ArrayList<Future>();

    final AtomicInteger downloaded = new AtomicInteger();
    int total = 0;
    for (DownloadData each : toDownload.values()) {
      total += each.classifiersWithExtensions.size();
    }
    try {
      for (final Map.Entry<MavenId, DownloadData> eachEntry : toDownload.entrySet()) {
        myProgress.checkCanceled();

        final DownloadData data = eachEntry.getValue();
        final MavenId id = eachEntry.getKey();

        for (final Pair<String, String> eachTypeWithClassifier : data.classifiersWithExtensions) {
          final int finalTotal = total;
          futures.add(EXECUTOR.submit(new Runnable() {
            public void run() {
              try {
                myProgress.checkCanceled();
                myProgress.setFraction(((double)downloaded.getAndIncrement()) / finalTotal);

                Artifact a = myEmbedder.resolve(id, eachTypeWithClassifier.second, eachTypeWithClassifier.first,
                                                new ArrayList<MavenRemoteRepository>(data.repositories));
                File file = a.getFile();
                if (file != null && file.exists()) downloadedFiles.add(file);
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
  }

  private static class DownloadData {
    public final LinkedHashSet<MavenRemoteRepository> repositories = new LinkedHashSet<MavenRemoteRepository>();
    public final LinkedHashSet<Pair<String, String>> classifiersWithExtensions = new LinkedHashSet<Pair<String, String>>();
  }
}
