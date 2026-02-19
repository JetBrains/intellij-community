// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.rmi.IdeaWatchdog;
import com.intellij.execution.rmi.IdeaWatchdogAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.server.security.MavenToken;

import java.rmi.RemoteException;

public abstract class MavenWatchdogAware extends MavenRemoteObject implements IdeaWatchdogAware {
  private volatile IdeaWatchdog myWatchdog;

  @Override
  public void setWatchdog(@NotNull IdeaWatchdog watchdog) {
    myWatchdog = watchdog;
  }

  public boolean ping(MavenToken token) throws RemoteException {
    MavenServerUtil.checkToken(token);
    if (null == myWatchdog) return false;
    return myWatchdog.ping();
  }

}
