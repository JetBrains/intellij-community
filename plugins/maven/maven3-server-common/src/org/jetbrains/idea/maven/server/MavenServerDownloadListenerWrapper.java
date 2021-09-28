/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server;


import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MavenServerDownloadListenerWrapper extends MavenRemoteObject
  implements MavenServerDownloadListener, MavenPullDownloadListener {
  private final ConcurrentLinkedQueue<DownloadArtifactEvent> myPullingQueue = new ConcurrentLinkedQueue<DownloadArtifactEvent>();

  @Override
  public void artifactDownloaded(File file, String relativePath) throws RemoteException {
    myPullingQueue.add(new DownloadArtifactEvent(file.getAbsolutePath(), relativePath));
  }

  @Override
  @Nullable
  public List<DownloadArtifactEvent> pull() {
    return MavenRemotePullUtil.pull(myPullingQueue);
  }
}
