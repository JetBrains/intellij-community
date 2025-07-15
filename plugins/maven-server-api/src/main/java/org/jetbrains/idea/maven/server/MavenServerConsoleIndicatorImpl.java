// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MavenServerConsoleIndicatorImpl implements MavenServerConsoleIndicator {

  private final ConcurrentLinkedQueue<MavenArtifactEvent> myPullingQueue = new ConcurrentLinkedQueue<>();

  private final ConcurrentLinkedQueue<MavenServerConsoleEvent> myConsoleEventsQueue = new ConcurrentLinkedQueue<>();

  private final ConcurrentLinkedQueue<DownloadArtifactEvent> myDowLoadArtifactEventsQueue = new ConcurrentLinkedQueue<>();

  private boolean myCancelled = false;

  @Override
  public void startedDownload(ResolveType type, String dependencyId) {
    myPullingQueue.add(new MavenArtifactEvent(type,
                                              MavenArtifactEvent.ArtifactEventType.DOWNLOAD_STARTED,
                                              dependencyId,
                                              null,
                                              null));
  }

  @Override
  public void completedDownload(ResolveType type, String dependencyId) {
    myPullingQueue.add(new MavenArtifactEvent(type,
                                              MavenArtifactEvent.ArtifactEventType.DOWNLOAD_COMPLETED,
                                              dependencyId,
                                              null, null));
  }

  @Override
  public void failedDownload(ResolveType type, String dependencyId, String errorMessage, String stackTrace) {
    myPullingQueue.add(new MavenArtifactEvent(type,
                                              MavenArtifactEvent.ArtifactEventType.DOWNLOAD_FAILED,
                                              dependencyId,
                                              errorMessage, stackTrace));
  }

  @Override
  public boolean isCanceled() {
    return myCancelled;
  }

  @Override
  public void cancel() {
    myCancelled = true;
  }

  @Override
  public @NotNull List<MavenArtifactEvent> pullDownloadEvents() {
    return MavenRemotePullUtil.pull(myPullingQueue);
  }

  @Override
  public @NotNull List<DownloadArtifactEvent> pullDownloadArtifactEvents() {
    return MavenRemotePullUtil.pull(myDowLoadArtifactEventsQueue);
  }

  @Override
  public @NotNull List<MavenServerConsoleEvent> pullConsoleEvents() {
    return MavenRemotePullUtil.pull(myConsoleEventsQueue);
  }

  public void printMessage(int level, String message, Throwable throwable) {
    myConsoleEventsQueue.add(new MavenServerConsoleEvent(level, message, throwable));
  }

  private void printMessage(int level, String message) {
    printMessage(level, message, null);
  }

  public void debug(String message) {
    printMessage(MavenServerConsoleIndicator.LEVEL_DEBUG, message);
  }

  public void info(String message) {
    printMessage(MavenServerConsoleIndicator.LEVEL_INFO, message);
  }

  public void warn(String message) {
    printMessage(MavenServerConsoleIndicator.LEVEL_WARN, message);
  }

  public void error(String message) {
    printMessage(MavenServerConsoleIndicator.LEVEL_ERROR, message);
  }

  public void artifactDownloaded(File file) {
    myDowLoadArtifactEventsQueue.add(new DownloadArtifactEvent(file.getAbsolutePath()));
  }
}
