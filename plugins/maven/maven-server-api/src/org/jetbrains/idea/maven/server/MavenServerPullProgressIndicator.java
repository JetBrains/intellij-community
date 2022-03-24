// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.Nullable;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface MavenServerPullProgressIndicator extends Remote {

  @Nullable
  List<MavenArtifactDownloadServerProgressEvent> pullDownloadEvents() throws RemoteException;

  @Nullable
  List<MavenServerConsoleEvent> pullConsoleEvents() throws RemoteException;

   void cancel() throws RemoteException;
}
