// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface MavenServerConsoleIndicator extends Remote {
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
  
  void startedDownload(ResolveType type, String dependencyId) throws RemoteException;

  void completedDownload(ResolveType type, String dependencyId) throws RemoteException;

  void failedDownload(ResolveType type, String dependencyId, String errorMessage, String stackTrace) throws RemoteException;

  boolean isCanceled() throws RemoteException;
  
  @NotNull
  List<MavenArtifactDownloadServerProgressEvent> pullDownloadEvents() throws RemoteException;

  @NotNull
  List<MavenServerConsoleEvent> pullConsoleEvents() throws RemoteException;

   void cancel() throws RemoteException;
}
