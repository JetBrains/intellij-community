// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.rmi.RemoteException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class MavenServerConnectorBase extends AbstractMavenServerConnector {
  protected final Integer myDebugPort;
  protected MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport mySupport;

  protected final AtomicBoolean myConnectStarted = new AtomicBoolean(false);
  protected final AtomicBoolean myTerminated = new AtomicBoolean(false);
  protected boolean throwExceptionIfProjectDisposed = true;

  protected final AsyncPromise<@NotNull MavenServer> myServerPromise = new AsyncPromise<>() {
    @Override
    protected boolean shouldLogErrors() {
      return false;
    }
  };

  MavenServerConnectorBase(@Nullable Project project,
                           @NotNull Sdk jdk,
                           @NotNull String vmOptions,
                           @NotNull MavenDistribution mavenDistribution,
                           @NotNull String multimoduleDirectory, @Nullable Integer debugPort) {
    super(project, jdk, vmOptions, mavenDistribution, multimoduleDirectory);
    myDebugPort = debugPort;
  }

  @Override
  public boolean isNew() {
    return !myConnectStarted.get();
  }

  @NotNull
  protected abstract Runnable newStartServerTask();

  @Override
  public void connect() {
    if (!myConnectStarted.compareAndSet(false, true)) {
      return;
    }
    MavenLog.LOG.debug("connecting new maven server: " + this);
    ApplicationManager.getApplication().executeOnPooledThread(newStartServerTask());
  }

  @Nullable
  protected MavenServer waitForServer() {
    while (!myServerPromise.isDone()) {
      try {
        myServerPromise.get(100, TimeUnit.MILLISECONDS);
      }
      catch (Exception ignore) {
      }
      if (throwExceptionIfProjectDisposed && myProject.isDisposed()) {
        throw new CannotStartServerException("Project already disposed");
      }
      ProgressManager.checkCanceled();
    }
    return myServerPromise.get();
  }

  @NotNull
  @Override
  protected MavenServer getServer() {
    try {
      MavenServer server = waitForServer();
      if (server == null) {
        throw new ProcessCanceledException();
      }
      return server;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      try {
        MavenServerManager.getInstance().shutdownConnector(this, false);
      }
      catch (Throwable ignored) {
      }
      throw e instanceof CannotStartServerException
            ? (CannotStartServerException)e
            : new CannotStartServerException(e);
    }
  }

  @ApiStatus.Internal
  @Override
  public void stop(boolean wait) {
    MavenLog.LOG.debug("[connector] shutdown " + this + " " + (mySupport == null));
    cleanUpFutures();
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    if (support != null) {
      support.stopAll(wait);
    }
    myTerminated.set(true);
  }

  @Override
  protected <R, E extends Exception> R perform(Retriable<R, E> r) throws E {
    RemoteException last = null;
    for (int i = 0; i < 2; i++) {
      try {
        return r.execute();
      }
      catch (RemoteException e) {
        last = e;
      }
    }
    cleanUpFutures();
    MavenServerManager.getInstance().shutdownConnector(this, false);
    MavenLog.LOG.debug("[connector] perform error " + this);
    throw new RuntimeException("Cannot reconnect.", last);
  }

  protected abstract void cleanUpFutures();

  @Override
  public State getState() {
    return switch (myServerPromise.getState()) {
      case SUCCEEDED -> myTerminated.get() ? State.STOPPED : State.RUNNING;
      case REJECTED -> State.FAILED;
      default -> State.STARTING;
    };
  }

  @Override
  public boolean checkConnected() {
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    return support != null && !support.getActiveConfigurations().isEmpty();
  }

  @Override
  public boolean ping() {
    try {
      boolean pinged = getServer().ping(MavenRemoteObjectWrapper.ourToken);
      if (MavenLog.LOG.isTraceEnabled()) {
        MavenLog.LOG.trace("maven server ping: " + pinged);
      }
      return pinged;
    }
    catch (RemoteException e) {
      MavenLog.LOG.warn("maven server ping error", e);
      return false;
    }
  }
}
