// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.idea.maven.server.security.MavenToken;

import java.rmi.server.UnicastRemoteObject;

public abstract class MavenServerBase extends MavenWatchdogAware implements MavenServer {
  @Override
  public MavenPullServerLogger createPullLogger(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      MavenServerLoggerWrapper result = MavenServerGlobals.getLogger();
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (Throwable e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public MavenPullDownloadListener createPullDownloadListener(MavenToken token) {
    MavenServerUtil.checkToken(token);
    try {
      MavenServerDownloadListenerWrapper result = MavenServerGlobals.getDownloadListener();
      UnicastRemoteObject.exportObject(result, 0);
      return result;
    }
    catch (Throwable e) {
      throw wrapToSerializableRuntimeException(e);
    }
  }

  @Override
  public synchronized void unreferenced() {
    System.exit(0);
  }
}
