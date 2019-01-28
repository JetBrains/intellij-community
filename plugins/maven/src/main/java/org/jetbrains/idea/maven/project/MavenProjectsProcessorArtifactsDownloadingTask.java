// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collection;

public class MavenProjectsProcessorArtifactsDownloadingTask implements MavenProjectsProcessorTask {
  private final Collection<MavenProject> myProjects;
  private final Collection<MavenArtifact> myArtifacts;
  private final MavenProjectsTree myTree;
  private final boolean myDownloadSources;
  private final boolean myDownloadDocs;
  private final AsyncPromise<? super MavenArtifactDownloader.DownloadResult> myCallbackResult;

  public MavenProjectsProcessorArtifactsDownloadingTask(Collection<MavenProject> projects,
                                                        Collection<MavenArtifact> artifacts,
                                                        MavenProjectsTree tree,
                                                        boolean downloadSources,
                                                        boolean downloadDocs,
                                                        AsyncPromise<? super MavenArtifactDownloader.DownloadResult> callbackResult) {
    myProjects = projects;
    myArtifacts = artifacts;
    myTree = tree;
    myDownloadSources = downloadSources;
    myDownloadDocs = downloadDocs;
    myCallbackResult = callbackResult;
  }

  @Override
  public void perform(final Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    MavenArtifactDownloader.DownloadResult result =
      myTree.downloadSourcesAndJavadocs(project, myProjects, myArtifacts, myDownloadSources, myDownloadDocs, embeddersManager, console, indicator);
    if (myCallbackResult != null) {
      myCallbackResult.setResult(result);
    }

    // todo: hack to update all file pointers.
    MavenUtil.invokeLater(project, () -> WriteAction.run(
      () -> ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true)));
  }
}