// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.Collection;

public class MavenProjectsProcessorArtifactsDownloadingTask implements MavenProjectsProcessorTask {
  private final Collection<MavenProject> myProjects;
  private final Collection<MavenArtifact> myArtifacts;
  private final boolean myDownloadSources;
  private final boolean myDownloadDocs;
  private final AsyncPromise<? super MavenArtifactDownloader.DownloadResult> myCallbackResult;
  private final MavenProjectResolver myResolver;

  public MavenProjectsProcessorArtifactsDownloadingTask(Collection<MavenProject> projects,
                                                        Collection<MavenArtifact> artifacts,
                                                        MavenProjectResolver resolver,
                                                        boolean downloadSources,
                                                        boolean downloadDocs,
                                                        AsyncPromise<? super MavenArtifactDownloader.DownloadResult> callbackResult) {
    myProjects = projects;
    myArtifacts = artifacts;
    myResolver = resolver;
    myDownloadSources = downloadSources;
    myDownloadDocs = downloadDocs;
    myCallbackResult = callbackResult;
  }

  @Override
  public void perform(final Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    MavenArtifactDownloader.DownloadResult result =
      myResolver.downloadSourcesAndJavadocs(project, myProjects, myArtifacts, myDownloadSources, myDownloadDocs, embeddersManager, console, indicator);
    if (myCallbackResult != null) {
      myCallbackResult.setResult(result);
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      VirtualFileManager.getInstance().asyncRefresh(null);
    });
  }
}