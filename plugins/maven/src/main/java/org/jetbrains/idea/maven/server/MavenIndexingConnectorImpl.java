// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
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

public class MavenIndexingConnectorImpl extends MavenServerConnector {
  public static final Logger LOG = Logger.getInstance(MavenIndexingConnectorImpl.class);
  protected final Integer myDebugPort;
  private final AtomicBoolean myConnectStarted = new AtomicBoolean(false);
  private final AtomicBoolean myTerminated = new AtomicBoolean(false);
  private MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport mySupport;
  private final AsyncPromise<@NotNull MavenServer> myServerPromise = new AsyncPromise<>() {
    @Override
    protected boolean shouldLogErrors() {
      return false;
    }
  };

  public MavenIndexingConnectorImpl(@NotNull MavenServerManager manager,
                                    @NotNull Sdk jdk,
                                    @NotNull String vmOptions,
                                    @Nullable Integer debugPort,
                                    @NotNull MavenDistribution mavenDistribution,
                                    @NotNull String multimoduleDirectory) {
    super(null, manager, jdk, vmOptions, mavenDistribution, multimoduleDirectory);
    myDebugPort = debugPort;
  }

  @Override
  boolean isNew() {
    return !myConnectStarted.get();
  }

  @Override
  public boolean isCompatibleWith(Sdk jdk, String vmOptions, MavenDistribution distribution) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void connect() {
    if (!myConnectStarted.compareAndSet(false, true)) {
      return;
    }
    MavenLog.LOG.debug("connecting new maven server:", new Exception());
    ApplicationManager.getApplication().executeOnPooledThread(new StartIndexingServerTask());
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
      myManager.cleanUp(this);
      throw e instanceof CannotStartServerException
            ? (CannotStartServerException)e
            : new CannotStartServerException(e);
    }
  }

  @Nullable
  private MavenServer waitForServer() {
    while (!myServerPromise.isDone()) {
      try {
        myServerPromise.get(100, TimeUnit.MILLISECONDS);
      }
      catch (Exception ignore) {
      }
      ProgressManager.checkCanceled();
    }
    return myServerPromise.get();
  }

  @ApiStatus.Internal
  @Override
  public void stop(boolean wait) {
    MavenLog.LOG.debug("[connector] shutdown " + this + " " + (mySupport == null));
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    if (support != null) {
      support.stopAll(wait);
    }
    myTerminated.set(true);
  }


  @Override
  protected <R, E extends Exception> R perform(RemoteObjectWrapper.Retriable<R, E> r) throws E {
    RemoteException last = null;
    for (int i = 0; i < 2; i++) {
      try {
        return r.execute();
      }
      catch (RemoteException e) {
        last = e;
      }
    }
    myManager.shutdownConnector(this, false);
    MavenLog.LOG.debug("[connector] perform error " + this);
    throw new RuntimeException("Cannot reconnect.", last);
  }

  @Override
  public String getSupportType() {
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    return support == null ? "INDEX-?" : "INDEX-" + support.type();
  }

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


  private class StartIndexingServerTask implements Runnable {
    @Override
    public void run() {
      ProgressIndicator indicator = new EmptyProgressIndicator();
      String dirForLogs = myMultimoduleDirectories.iterator().next();
      MavenLog.LOG.debug("Connecting maven connector in " + dirForLogs);
      try {
        if (myDebugPort != null) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Listening for transport dt_socket at address: " + myDebugPort);
        }
        MavenRemoteProcessSupportFactory factory = MavenRemoteProcessSupportFactory.forIndexer();
        mySupport = factory.createIndexerSupport(myJdk, myVmOptions, myDistribution, myDebugPort);
        mySupport.onTerminate(e -> {
          MavenLog.LOG.debug("[connector] terminate " + MavenIndexingConnectorImpl.this);
          MavenServerManager.getInstance().shutdownConnector(MavenIndexingConnectorImpl.this, false);
        });
        MavenServer server = mySupport.acquire(this, "", indicator);
        myServerPromise.setResult(server);
        MavenLog.LOG.debug("[connector] in " + dirForLogs + " has been connected " + MavenIndexingConnectorImpl.this);
      }
      catch (Throwable e) {
        MavenLog.LOG.warn("[connector] cannot connect in " + dirForLogs, e);
        myServerPromise.setError(e);
      }
    }
  }
}


