// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;


import org.jetbrains.idea.maven.server.embedder.Maven2ServerLoggerWrapper;

public final class Maven2ServerGlobals {
  private static Maven2ServerLoggerWrapper myLogger;
  private static MavenServerDownloadListener myDownloadListener;

  public static Maven2ServerLoggerWrapper getLogger() {
    return myLogger;
  }

  public static MavenServerDownloadListener getDownloadListener() {
    return myDownloadListener;
  }


  public static void set(MavenServerLogger logger, MavenServerDownloadListener downloadListener) {
    myLogger = new Maven2ServerLoggerWrapper(logger);
    myDownloadListener = downloadListener;
  }
}
