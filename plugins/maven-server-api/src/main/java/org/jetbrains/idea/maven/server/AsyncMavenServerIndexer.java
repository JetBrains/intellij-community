// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.io.File;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface AsyncMavenServerIndexer extends MavenServerIndexer {
  MavenIndexUpdateState startIndexing(MavenRepositoryInfo repositoryInfo, File indexDir, MavenToken token) throws RemoteException;

  ArrayList<MavenIndexUpdateState> status(MavenToken token) throws RemoteException;

  void stopIndexing(MavenRepositoryInfo info, MavenToken token) throws RemoteException;
}
