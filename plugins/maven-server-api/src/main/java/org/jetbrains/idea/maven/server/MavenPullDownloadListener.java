// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.Nullable;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface MavenPullDownloadListener extends Remote {
  @Nullable
  List<DownloadArtifactEvent> pull() throws RemoteException;
}
