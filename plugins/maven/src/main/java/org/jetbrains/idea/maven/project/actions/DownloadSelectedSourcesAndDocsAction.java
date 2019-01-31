// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenArtifactDownloader;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DownloadSelectedSourcesAndDocsAction extends MavenProjectsAction {
  private final boolean mySources;
  private final boolean myDocs;

  @SuppressWarnings({"UnusedDeclaration"})
  public DownloadSelectedSourcesAndDocsAction() {
    this(true, true);
  }

  public DownloadSelectedSourcesAndDocsAction(boolean sources, boolean docs) {
    mySources = sources;
    myDocs = docs;
  }

  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    return super.isAvailable(e) && !getDependencies(e).isEmpty();
  }

  private static Collection<MavenArtifact> getDependencies(AnActionEvent e) {
    Collection<MavenArtifact> result = e.getData(MavenDataKeys.MAVEN_DEPENDENCIES);
    return result == null ? Collections.emptyList() : result;
  }

  @Override
  protected void perform(@NotNull MavenProjectsManager manager, List<MavenProject> mavenProjects, AnActionEvent e) {
    manager.scheduleArtifactsDownloading(mavenProjects, getDependencies(e), mySources, myDocs,
                                         (AsyncPromise<MavenArtifactDownloader.DownloadResult>)null);
  }
}