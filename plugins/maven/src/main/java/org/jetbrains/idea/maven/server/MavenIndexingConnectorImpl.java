// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenLog;

public class MavenIndexingConnectorImpl extends MavenServerConnectorBase {
  public static final Logger LOG = Logger.getInstance(MavenIndexingConnectorImpl.class);

  public MavenIndexingConnectorImpl(@NotNull Sdk jdk,
                                    @NotNull String vmOptions,
                                    @Nullable Integer debugPort,
                                    @NotNull MavenDistribution mavenDistribution,
                                    @NotNull String multimoduleDirectory) {
    super(null, jdk, vmOptions, mavenDistribution, multimoduleDirectory, debugPort);
    throwExceptionIfProjectDisposed = false;
  }

  @Override
  public boolean isCompatibleWith(Sdk jdk, String vmOptions, MavenDistribution distribution) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected StartIndexingServerTask newStartServerTask() {
    return new StartIndexingServerTask();
  }

  @Override
  protected void cleanUpFutures() {
  }

  @Override
  public String getSupportType() {
    MavenRemoteProcessSupportFactory.MavenRemoteProcessSupport support = mySupport;
    return support == null ? "INDEX-?" : "INDEX-" + support.type();
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
        // Maven server's lifetime is bigger than the activity that spawned it, so we let it go untracked
        try (AccessToken ignored = ThreadContext.resetThreadContext()) {
          MavenServer server = mySupport.acquire(this, "", indicator);
          myServerPromise.setResult(server);
        }
        MavenLog.LOG.debug("[connector] in " + dirForLogs + " has been connected " + MavenIndexingConnectorImpl.this);
      }
      catch (Throwable e) {
        MavenLog.LOG.warn("[connector] cannot connect in " + dirForLogs, e);
        myServerPromise.setError(e);
      }
    }
  }
}


