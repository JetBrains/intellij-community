// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public final class LongRunningTaskStatus implements Serializable {
  public static final LongRunningTaskStatus EMPTY = new LongRunningTaskStatus(
    0, 0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

  private final int total;
  private final int finished;
  private final List<MavenServerConsoleEvent> consoleEvents;
  private final List<MavenArtifactEvent> downloadEvents;
  private final List<DownloadArtifactEvent> downloadArtifactEvents;

  public LongRunningTaskStatus(int total,
                               int finished,
                               List<MavenServerConsoleEvent> consoleEvents,
                               List<MavenArtifactEvent> downloadEvents,
                               List<DownloadArtifactEvent> downloadArtifactEvents) {
    this.total = total;
    this.finished = finished;
    this.consoleEvents = consoleEvents;
    this.downloadEvents = downloadEvents;
    this.downloadArtifactEvents = downloadArtifactEvents;
  }

  public int total() { return total; }

  public int finished() { return finished; }

  public double fraction() {
    int t = total;
    int f = finished;
    if (t == 0) return 0;
    return ((double)f) / t;
  }

  public List<MavenServerConsoleEvent> consoleEvents() {
    return consoleEvents;
  }

  public List<MavenArtifactEvent> downloadEvents() {
    return downloadEvents;
  }

  public List<DownloadArtifactEvent> downloadArtifactEvents() {
    return downloadArtifactEvents;
  }

  @Override
  public String toString() {
    return "LongRunningTaskStatus[" +
           "total=" + total + ", " +
           "finished=" + finished + ']';
  }
}
