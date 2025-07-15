// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MavenServerConsoleIndicator {
  // must be same as in org.codehaus.plexus.logging.Logger
  int LEVEL_DEBUG = 0;
  int LEVEL_INFO = 1;
  int LEVEL_WARN = 2;
  int LEVEL_ERROR = 3;
  int LEVEL_FATAL = 4;
  int LEVEL_DISABLED = 5;

  enum ResolveType {
    DEPENDENCY,
    PLUGIN
  }

  void startedDownload(ResolveType type, String dependencyId);

  void completedDownload(ResolveType type, String dependencyId);

  void failedDownload(ResolveType type, String dependencyId, String errorMessage, String stackTrace);

  boolean isCanceled();
  
  @NotNull
  List<MavenArtifactEvent> pullDownloadEvents();

  @NotNull List<DownloadArtifactEvent> pullDownloadArtifactEvents();

  @NotNull
  List<MavenServerConsoleEvent> pullConsoleEvents();

   void cancel();
}
