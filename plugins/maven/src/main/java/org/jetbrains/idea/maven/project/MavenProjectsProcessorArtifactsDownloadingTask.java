// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.Collection;

public final class MavenProjectsProcessorArtifactsDownloadingTask implements MavenProjectsProcessorTask {

  private final @NotNull Collection<MavenProject> myProjects;
  private final @Nullable Collection<MavenArtifact> myArtifacts;
  private final @NotNull MavenProjectResolver myResolver;
  private final boolean myDownloadSources;
  private final boolean myDownloadDocs;
  private final @Nullable AsyncPromise<? super MavenArtifactDownloader.DownloadResult> myCallbackResult;

  public MavenProjectsProcessorArtifactsDownloadingTask(@NotNull Collection<MavenProject> projects,
                                                        @Nullable Collection<MavenArtifact> artifacts,
                                                        @NotNull MavenProjectResolver resolver,
                                                        boolean downloadSources,
                                                        boolean downloadDocs,
                                                        @Nullable AsyncPromise<? super MavenArtifactDownloader.DownloadResult> callbackResult) {
    myProjects = projects;
    myArtifacts = artifacts;
    myResolver = resolver;
    myDownloadSources = downloadSources;
    myDownloadDocs = downloadDocs;
    myCallbackResult = callbackResult;
  }

  @Override
  public void perform(@NotNull Project project,
                      @NotNull MavenEmbeddersManager embeddersManager,
                      @NotNull MavenConsole console,
                      @NotNull MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    MavenArtifactDownloader.DownloadResult result = myResolver.downloadSourcesAndJavadocs(project,
                                                                                          myProjects,
                                                                                          myArtifacts,
                                                                                          myDownloadSources,
                                                                                          myDownloadDocs,
                                                                                          embeddersManager,
                                                                                          console,
                                                                                          indicator);
    if (myCallbackResult != null) {
      myCallbackResult.setResult(result);
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      VirtualFileManager.getInstance().asyncRefresh(null);
    }, project.getDisposed());
  }
}