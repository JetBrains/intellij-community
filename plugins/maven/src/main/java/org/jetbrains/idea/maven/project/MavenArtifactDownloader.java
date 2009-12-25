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
import com.intellij.openapi.vfs.LocalFileSystem;
import gnu.trove.THashMap;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MavenArtifactDownloader {
  private final static ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(5, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

  private final MavenEmbedderWrapper myEmbedder;
  private final MavenProgressIndicator myProgress;
  private final MavenProjectsTree myProjectsTree;
  private final List<MavenProject> myMavenProjects;

  public static void download(MavenProjectsTree projectsTree,
                              List<MavenProject> mavenProjects,
                              boolean downloadSources,
                              boolean downloadJavadoc, MavenEmbedderWrapper embedder,
                              MavenProgressIndicator p) throws MavenProcessCanceledException {
    new MavenArtifactDownloader(projectsTree, mavenProjects, embedder, p).download(downloadSources, downloadJavadoc);
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

  private void download(boolean downloadSources, boolean downloadJavadoc) throws MavenProcessCanceledException {
    List<File> downloadedFiles = new ArrayList<File>();
    try {
      Map<MavenId, Set<MavenRemoteRepository>> artifacts = collectArtifactsToDownload();

      download(downloadSources, downloadJavadoc, artifacts, downloadedFiles);
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

  private Map<MavenId, Set<MavenRemoteRepository>> collectArtifactsToDownload() {
    Map<MavenId, Set<MavenRemoteRepository>> result = new THashMap<MavenId, Set<MavenRemoteRepository>>();

    for (MavenProject eachProject : myMavenProjects) {
      List<MavenRemoteRepository> repositories = eachProject.getRemoteRepositories();

      for (MavenArtifact eachDependency : eachProject.getDependencies()) {
        if (Artifact.SCOPE_SYSTEM.equalsIgnoreCase(eachDependency.getScope())) continue;
        if (myProjectsTree.findProject(eachDependency.getMavenId()) != null) continue;
        if (!eachProject.isSupportedDependency(eachDependency)) continue;
        if (!eachDependency.isResolved()) continue;

        MavenId depId = eachDependency.getMavenId();
        Set<MavenRemoteRepository> registeredRepositories = result.get(depId);
        if (registeredRepositories == null) {
          registeredRepositories = new LinkedHashSet<MavenRemoteRepository>();
          result.put(depId, registeredRepositories);
        }
        registeredRepositories.addAll(repositories);
      }
    }
    return result;
  }

  private void download(final boolean downloadSources,
                        final boolean downloadJavadoc,
                        final Map<MavenId, Set<MavenRemoteRepository>> libraryArtifacts,
                        final List<File> downloadedFiles) throws MavenProcessCanceledException {
    List<Future> futures = new ArrayList<Future>();

    List<String> classifiers = new ArrayList<String>(2);
    if (downloadSources) classifiers.add(MavenConstants.CLASSIFIER_SOURCES);
    if (downloadJavadoc) classifiers.add(MavenConstants.CLASSIFIER_JAVADOC);

    final AtomicInteger downloaded = new AtomicInteger();
    final int total = libraryArtifacts.size() * classifiers.size();
    try {
      for (final Map.Entry<MavenId, Set<MavenRemoteRepository>> eachEntry : libraryArtifacts.entrySet()) {
        myProgress.checkCanceled();

        for (final String eachClassifier : classifiers) {
          futures.add(EXECUTOR.submit(new Runnable() {
            public void run() {
              try {
                myProgress.checkCanceled();
                myProgress.setFraction(((double)downloaded.getAndIncrement()) / total);

                Artifact a = myEmbedder.resolve(eachEntry.getKey(), MavenConstants.TYPE_JAR, eachClassifier,
                                                new ArrayList<MavenRemoteRepository>(eachEntry.getValue()));
                if (a.isResolved()) downloadedFiles.add(a.getFile());
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
}
