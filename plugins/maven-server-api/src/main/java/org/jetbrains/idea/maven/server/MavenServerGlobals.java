// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;


public final class MavenServerGlobals {
  private static final MavenServerLoggerWrapper myLogger = new MavenServerLoggerWrapper();
  private static final MavenServerDownloadListenerWrapper myDownloadListener = new MavenServerDownloadListenerWrapper();

  public static MavenServerLoggerWrapper getLogger() {
    return myLogger;
  }

  public static MavenServerDownloadListenerWrapper getDownloadListener() {
    return myDownloadListener;
  }

}
