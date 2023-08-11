// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server.indexer;

import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;

import java.rmi.RemoteException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * the purpose of this class is to get rid of check canceled calls, but still allow to track progress
 */
public class MavenServerSideCancelledThrottler implements MavenServerProgressIndicator {
  private static final long CHECK_PERIOD = 2000;
  private final AtomicLong lastTime = new AtomicLong(System.currentTimeMillis());
  private final MavenServerProgressIndicator myIndicator;
  private volatile boolean cancelled = false;

  public MavenServerSideCancelledThrottler(MavenServerProgressIndicator indicator) { myIndicator = indicator; }

  @Override
  public void setText(String text) throws RemoteException {
    myIndicator.setText(text);
  }

  @Override
  public void setText2(String text) throws RemoteException {
    myIndicator.setText2(text);
  }

  @Override
  public boolean isCanceled() throws RemoteException {
    if (cancelled) return true;
    if (lastTime.get() + CHECK_PERIOD > System.currentTimeMillis()) return false;
    lastTime.set(System.currentTimeMillis());
    cancelled =  myIndicator.isCanceled();
    return cancelled;
  }

  @Override
  public void setIndeterminate(boolean value) throws RemoteException {
    myIndicator.setIndeterminate(value);
  }

  @Override
  public void setFraction(double fraction) throws RemoteException {
    myIndicator.setFraction(fraction);
  }
}
