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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MavenServerConnectorImpl extends MavenServerConnector {
  public static final Logger LOG = Logger.getInstance(MavenServerConnectorImpl.class);
  private final MavenServerDownloadDispatcher
    myDownloadListener = new MavenServerDownloadDispatcher();

  protected final Integer myDebugPort;


  private ScheduledFuture<?> myLoggerFuture;
  private ScheduledFuture<?> myDownloadListenerFuture;
  private final AtomicInteger myLoggerConnectFailedCount = new AtomicInteger(0);
  private final AtomicInteger myDownloadConnectFailedCount = new AtomicInteger(0);
  private final AtomicBoolean myConnectStarted = new AtomicBoolean(false);

  private MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport mySupport;
  private final AsyncPromise<@NotNull MavenServer> myServerPromise = new AsyncPromise<>() {
    @Override
    protected boolean shouldLogErrors() {
      return false;
    }
  };


  public MavenServerConnectorImpl(@NotNull Project project,
                                  @NotNull MavenServerManager manager,
                                  @NotNull Sdk jdk,
                                  @NotNull String vmOptions,
                                  @Nullable Integer debugPort,
                                  @NotNull MavenDistribution mavenDistribution,
                                  @NotNull String multimoduleDirectory) {
    super(project, manager, jdk, vmOptions, mavenDistribution, multimoduleDirectory);
    myDebugPort = debugPort;
  }

  @Override
  boolean isNew() {
    return !myConnectStarted.get();
  }

  @Override
  public boolean isCompatibleWith(Sdk jdk, String vmOptions, MavenDistribution distribution) {
    if (!myDistribution.compatibleWith(distribution)) {
      return false;
    }
    if (!StringUtil.equals(myJdk.getName(), jdk.getName())) {
      return false;
    }
    return StringUtil.equals(vmOptions, myVmOptions);
  }

  @Override
  protected void connect() {
    if (!myConnectStarted.compareAndSet(false, true)) {
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(new StartServerTask());
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
        shutdown(false);
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
      if (myProject.isDisposed()) {
        throw new CannotStartServerException("Project already disposed");
      }
      ProgressManager.checkCanceled();
    }
    return myServerPromise.get();
  }

  private void cleanUp() {
    if (myLoggerFuture != null) {
      int count = myLoggerConnectFailedCount.get();
      if (count != 0) MavenLog.LOG.warn("Maven pulling logger was failed: " + count + " times");
      myLoggerFuture.cancel(true);
      myLoggerFuture = null;
    }
    if (myDownloadListenerFuture != null) {
      int count = myDownloadConnectFailedCount.get();
      if (count != 0) MavenLog.LOG.warn("Maven pulling download listener was failed: " + count + " times");
      myDownloadListenerFuture.cancel(true);
      myDownloadListenerFuture = null;
    }
  }

  @Override
  public void addDownloadListener(MavenServerDownloadListener listener) {
    myDownloadListener.myListeners.add(listener);
  }

  @Override
  public void removeDownloadListener(MavenServerDownloadListener listener) {
    myDownloadListener.myListeners.remove(listener);
  }

  @ApiStatus.Internal
  @Override
  public void shutdown(boolean wait) {
    MavenLog.LOG.debug("[connector] shutdown "  + this + " " + (mySupport == null));
    super.shutdown(true);
    cleanUp();
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    if (support != null) {
      support.stopAll(wait);
      mySupport = null;
    }
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
    cleanUp();
    myManager.cleanUp(this);
    MavenLog.LOG.debug("[connector] perform error " + this);
    throw new RuntimeException("Cannot reconnect.", last);
  }

  @Override
  public String getSupportType() {
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    return support == null ? "???" : support.type();
  }

  @Override
  public State getState() {
    switch (myServerPromise.getState()) {
      case SUCCEEDED: {
        return mySupport == null ? State.STOPPED : State.RUNNING;
      }
      case REJECTED:
        return State.FAILED;
      default:
        return State.STARTING;
    }
  }

  @Override
  public boolean checkConnected() {
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    return support!=null && !support.getActiveConfigurations().isEmpty();
  }

  private static class MavenServerDownloadDispatcher implements MavenServerDownloadListener {
    private final List<MavenServerDownloadListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

    @Override
    public void artifactDownloaded(File file, String relativePath) throws RemoteException {
      for (MavenServerDownloadListener each : myListeners) {
        each.artifactDownloaded(file, relativePath);
      }
    }
  }

  private class StartServerTask implements Runnable {
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
        MavenRemoteProcessSupportFactory factory = MavenRemoteProcessSupportFactory.forProject(myProject);
        mySupport = factory.create(myJdk, myVmOptions, myDistribution, myProject, myDebugPort);
        mySupport.onTerminate(e -> {
          MavenLog.LOG.debug("[connector] terminate " + MavenServerConnectorImpl.this);
          shutdown(false);
        });
        MavenServer server = mySupport.acquire(this, "", indicator);
        startPullingDownloadListener(server);
        startPullingLogger(server);
        myServerPromise.setResult(server);
        MavenLog.LOG.debug("[connector] in " + dirForLogs + " has been connected " + MavenServerConnectorImpl.this);
      }
      catch (Throwable e) {
        MavenLog.LOG.warn("[connector] cannot connect in " + dirForLogs, e);
        myServerPromise.setError(e);
      }
    }
  }

  private void startPullingDownloadListener(MavenServer server) throws RemoteException {
    MavenPullDownloadListener listener = server.createPullDownloadListener(MavenRemoteObjectWrapper.ourToken);
    if (listener == null) return;
    myDownloadListenerFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
      () -> {
        try {
          List<DownloadArtifactEvent> artifactEvents = listener.pull();
          if (artifactEvents == null) return;
          for (DownloadArtifactEvent e : artifactEvents) {
            myDownloadListener.artifactDownloaded(new File(e.getFile()), e.getPath());
          }
          myDownloadConnectFailedCount.set(0);
        }
        catch (RemoteException e) {
          if (!Thread.currentThread().isInterrupted()) {
            myDownloadConnectFailedCount.incrementAndGet();
          }
        }
      },
      500,
      500,
      TimeUnit.MILLISECONDS);
  }


  private void startPullingLogger(MavenServer server) throws RemoteException {
    MavenPullServerLogger logger = server.createPullLogger(MavenRemoteObjectWrapper.ourToken);
    if (logger == null) return;
    myLoggerFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
      () -> {
        try {
          List<ServerLogEvent> logEvents = logger.pull();
          if (logEvents == null) return;
          for (ServerLogEvent e : logEvents) {
            switch (e.getType()) {
              case PRINT:
              case INFO:
                MavenLog.LOG.info(e.getMessage());
                break;
              case WARN:
                MavenLog.LOG.warn(e.getMessage());
                break;
              case ERROR:
                MavenLog.LOG.error(e.getMessage());
                break;
            }
          }
          myLoggerConnectFailedCount.set(0);
        }
        catch (RemoteException e) {
          if (!Thread.currentThread().isInterrupted()) {
            myLoggerConnectFailedCount.incrementAndGet();
          }
        }
      },
      0,
      100,
      TimeUnit.MILLISECONDS);
  }
}


