// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;


import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MavenServerDownloadListenerWrapper extends MavenRemoteObject
  implements MavenServerDownloadListener, MavenPullDownloadListener {
  private final ConcurrentLinkedQueue<DownloadArtifactEvent> myPullingQueue = new ConcurrentLinkedQueue<DownloadArtifactEvent>();

  @Override
  public void artifactDownloaded(File file, String relativePath) {
    myPullingQueue.add(new DownloadArtifactEvent(file.getAbsolutePath(), relativePath));
  }

  @Override
  @Nullable
  public List<DownloadArtifactEvent> pull() {
    return MavenRemotePullUtil.pull(myPullingQueue);
  }
}
