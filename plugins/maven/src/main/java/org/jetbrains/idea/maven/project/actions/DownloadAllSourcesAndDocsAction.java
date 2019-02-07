// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.maven.project.MavenArtifactDownloader;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class DownloadAllSourcesAndDocsAction extends MavenProjectsManagerAction {
  private final boolean mySources;
  private final boolean myDocs;

  @SuppressWarnings({"UnusedDeclaration"})
  public DownloadAllSourcesAndDocsAction() {
    this(true, true);
  }

  public DownloadAllSourcesAndDocsAction(boolean sources, boolean docs) {
    mySources = sources;
    myDocs = docs;
  }

  @Override
  protected void perform(@NotNull MavenProjectsManager manager) {
    manager.scheduleArtifactsDownloading(manager.getProjects(), null, mySources, myDocs,
                                         (AsyncPromise<MavenArtifactDownloader.DownloadResult>)null);
  }
}