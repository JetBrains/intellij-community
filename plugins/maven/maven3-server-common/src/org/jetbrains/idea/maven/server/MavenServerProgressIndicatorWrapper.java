// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MavenServerProgressIndicatorWrapper extends MavenRemoteObject
  implements MavenServerProgressIndicator, MavenServerPullProgressIndicator, MavenServerConsole {

  private final ConcurrentLinkedQueue<MavenArtifactDownloadServerProgressEvent> myPullingQueue
    = new ConcurrentLinkedQueue<MavenArtifactDownloadServerProgressEvent>();

  private final ConcurrentLinkedQueue<MavenServerConsoleEvent> myConsoleEventsQueue
    = new ConcurrentLinkedQueue<MavenServerConsoleEvent>();

  private boolean myCancelled = false;


  @Override
  public void setText(String text) {
  }

  @Override
  public void setText2(String text) {
  }

  @Override
  public void startedDownload(ResolveType type, String dependencyId) {
    myPullingQueue.add(new MavenArtifactDownloadServerProgressEvent(type,
                                                                    MavenArtifactDownloadServerProgressEvent.ArtifactEventType.DOWNLOAD_STARTED,
                                                                    dependencyId,
                                                                    null,
                                                                    null));
  }

  @Override
  public void completedDownload(ResolveType type, String dependencyId) {
    myPullingQueue.add(new MavenArtifactDownloadServerProgressEvent(type,
                                                                    MavenArtifactDownloadServerProgressEvent.ArtifactEventType.DOWNLOAD_COMPLETED,
                                                                    dependencyId,
                                                                    null, null));
  }

  @Override
  public void failedDownload(ResolveType type, String dependencyId, String errorMessage, String stackTrace) {
    myPullingQueue.add(new MavenArtifactDownloadServerProgressEvent(type,
                                                                    MavenArtifactDownloadServerProgressEvent.ArtifactEventType.DOWNLOAD_FAILED,
                                                                    dependencyId,
                                                                    errorMessage, stackTrace));
  }

  @Override
  public boolean isCanceled() {
    return myCancelled;
  }

  @Override
  public void setIndeterminate(boolean value) {
  }

  @Override
  public void setFraction(double fraction) {
  }

  @Nullable
  @Override
  public List<MavenArtifactDownloadServerProgressEvent> pullDownloadEvents() {
    return MavenRemotePullUtil.pull(myPullingQueue);
  }

  @Nullable
  @Override
  public List<MavenServerConsoleEvent> pullConsoleEvents() {
    return MavenRemotePullUtil.pull(myConsoleEventsQueue);
  }

  @Override
  public void printMessage(int level, String message, Throwable throwable) {
    myConsoleEventsQueue.add(new MavenServerConsoleEvent(level, message, throwable));
  }

  @Override
  public void cancel() {
    myCancelled = true;
  }
}
