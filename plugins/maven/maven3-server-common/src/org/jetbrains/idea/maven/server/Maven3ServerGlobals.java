// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;


public final class Maven3ServerGlobals {
  private static MavenServerLoggerWrapper myLogger;
  private static MavenServerDownloadListener myDownloadListener;

  public static MavenServerLoggerWrapper getLogger() {
    return myLogger;
  }

  public static MavenServerDownloadListener getDownloadListener() {
    return myDownloadListener;
  }


  public static void set(MavenServerLogger logger, MavenServerDownloadListener downloadListener) {
    myLogger = new MavenServerLoggerWrapper(logger);
    myDownloadListener = downloadListener;
  }
}
