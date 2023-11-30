// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.File;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MavenServerConnectorImpl extends MavenServerConnectorBase {
  public static final Logger LOG = Logger.getInstance(MavenServerConnectorImpl.class);

  private final ScheduledExecutorService myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("Maven connector pulling", 2);
  private final AtomicInteger myLoggerConnectFailedCount = new AtomicInteger(0);
  private final AtomicInteger myDownloadConnectFailedCount = new AtomicInteger(0);

  private ScheduledFuture<?> myPullingLoggerFuture = null;
  private ScheduledFuture<?> myPullingDownloadFuture = null;


  public MavenServerConnectorImpl(@NotNull Project project,
                                  @NotNull Sdk jdk,
                                  @NotNull String vmOptions,
                                  @Nullable Integer debugPort,
                                  @NotNull MavenDistribution mavenDistribution,
                                  @NotNull String multimoduleDirectory) {
    super(project, jdk, vmOptions, mavenDistribution, multimoduleDirectory, debugPort);
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

  @NotNull
  @Override
  protected StartServerTask newStartServerTask() {
    return new StartServerTask();
  }

  @Override
  protected void cleanUpFutures() {
    try {
      cancelFuture(myPullingDownloadFuture);
      cancelFuture(myPullingLoggerFuture);
      if (!myExecutor.isShutdown()) {
        myExecutor.shutdownNow();
      }
      int count = myLoggerConnectFailedCount.get();
      if (count != 0) MavenLog.LOG.warn("Maven pulling logger failed: " + count + " times");
      count = myDownloadConnectFailedCount.get();
      if (count != 0) MavenLog.LOG.warn("Maven pulling download listener failed: " + count + " times");
    }
    catch (IllegalStateException ignore) {
    }
  }

  private static void cancelFuture(ScheduledFuture<?> future) {
    if (future != null) {
      try {
        future.cancel(true);
      }
      catch (Throwable ignore) {
      }
    }
  }

  @Override
  public String getSupportType() {
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    return support == null ? "???" : support.type();
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
          MavenServerManager mavenServerManager = ApplicationManager.getApplication().getServiceIfCreated(MavenServerManager.class);
          if (mavenServerManager != null) {
            mavenServerManager.shutdownConnector(MavenServerConnectorImpl.this, false);
          }
        });
        // Maven server's lifetime is bigger than the activity that spawned it, so we let it go untracked
        try (AccessToken ignored = ThreadContext.resetThreadContext()) {
          MavenServer server = mySupport.acquire(this, "", indicator);
          startPullingDownloadListener(server);
          startPullingLogger(server);
          myServerPromise.setResult(server);
        }
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
    myPullingDownloadFuture = myExecutor.scheduleWithFixedDelay(
      () -> {
        try {
          List<DownloadArtifactEvent> artifactEvents = listener.pull();
          for (DownloadArtifactEvent e : artifactEvents) {
            ApplicationManager.getApplication().getMessageBus().syncPublisher(DOWNLOAD_LISTENER_TOPIC).artifactDownloaded(new File(e.getFile()), e.getPath());
          }
          myDownloadConnectFailedCount.set(0);
        }
        catch (RemoteException e) {
          if (!Thread.currentThread().isInterrupted()) {
            myDownloadConnectFailedCount.incrementAndGet();
          }
          MavenLog.LOG.warn("Maven pulling download listener stopped");
          myPullingDownloadFuture.cancel(true);
        }
      },
      500,
      500,
      TimeUnit.MILLISECONDS);
  }


  private void startPullingLogger(MavenServer server) throws RemoteException {
    MavenPullServerLogger logger = server.createPullLogger(MavenRemoteObjectWrapper.ourToken);
    if (logger == null) return;
    myPullingLoggerFuture = myExecutor.scheduleWithFixedDelay(
      () -> {
        try {
          List<ServerLogEvent> logEvents = logger.pull();
          for (ServerLogEvent e : logEvents) {
            switch (e.getType()) {
              case DEBUG -> MavenLog.LOG.debug(e.getMessage());
              case PRINT, INFO -> MavenLog.LOG.info(e.getMessage());
              case WARN, ERROR -> MavenLog.LOG.warn(e.getMessage());
            }
          }
          myLoggerConnectFailedCount.set(0);
        }
        catch (RemoteException e) {
          if (!Thread.currentThread().isInterrupted()) {
            myLoggerConnectFailedCount.incrementAndGet();
          }
          MavenLog.LOG.warn("Maven pulling logger stopped");
          myPullingLoggerFuture.cancel(true);
        }
      },
      0,
      100,
      TimeUnit.MILLISECONDS);
  }
}


