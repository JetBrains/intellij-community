package com.intellij.ide.browsers;

import com.google.common.net.HostAndPort;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Urls;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * @author Sergey Simonchik
 */
public class BrowserStarter {
  private static final Logger LOG = Logger.getInstance(BrowserStarter.class);

  private final StartBrowserSettings mySettings;
  private final RunConfiguration myRunConfiguration;
  private final Computable<Boolean> myOutdated;

  public BrowserStarter(@NotNull RunConfiguration runConfiguration,
                        @NotNull StartBrowserSettings settings,
                        @NotNull Computable<Boolean> outdated) {
    mySettings = settings;
    myRunConfiguration = runConfiguration;
    myOutdated = outdated;
  }

  public BrowserStarter(@NotNull RunConfiguration runConfiguration,
                        @NotNull StartBrowserSettings settings,
                        @NotNull final ProcessHandler serverProcessHandler) {
    this(runConfiguration, settings, new Computable<Boolean>() {

      @Override
      public Boolean compute() {
        return serverProcessHandler.isProcessTerminating() || serverProcessHandler.isProcessTerminated();
      }
    });
  }

  public void start() {
    if (!mySettings.isSelected() || mySettings.getUrl() == null) {
      return;
    }

    HostAndPort hostAndPort = getHostAndPort(mySettings.getUrl());
    if (hostAndPort != null) {
      checkAndOpenPageLater(hostAndPort, 1, 300);
    }
    else {
      // we can't check page availability gracefully, so we just open it after some delay
      openPageLater(1000);
    }
  }

  @Nullable
  private static HostAndPort getHostAndPort(@NotNull String rawUrl) {
    URI url = Urls.parseAsJavaUriWithoutParameters(rawUrl);
    if (url == null) {
      return null;
    }

    int port = url.getPort();
    if (port == -1) {
      port = "https".equals(url.getScheme()) ? 443 : 80;
    }
    return HostAndPort.fromParts(StringUtil.notNullize(url.getHost(), "127.0.0.1"), port);
  }

  private void checkAndOpenPageLater(@NotNull final HostAndPort hostAndPort, final int attemptNumber, int delayMillis) {
    JobScheduler.getScheduler().schedule(new Runnable() {
      @Override
      public void run() {
        checkAndOpenPage(hostAndPort, attemptNumber);
      }
    }, delayMillis, TimeUnit.MILLISECONDS);
  }

  private void checkAndOpenPage(@NotNull final HostAndPort hostAndPort, final int attemptNumber) {
    if (NetUtils.canConnectToRemoteSocket(hostAndPort.getHostText(), hostAndPort.getPort())) {
      openPageNow();
    }
    else {
      LOG.info("[attempt#" + attemptNumber + "] Checking " + hostAndPort + " failed");
      if (!isOutdated()) {
        int delayMillis = getDelayMillis(attemptNumber);
        checkAndOpenPageLater(hostAndPort, attemptNumber + 1, delayMillis);
      }
    }
  }

  private static int getDelayMillis(int attemptNumber) {
    if (attemptNumber < 10) {
      return 400;
    }
    if (attemptNumber < 100) {
      return 1000;
    }
    return 2000;
  }

  private void openPageLater(int millis) {
    JobScheduler.getScheduler().schedule(new Runnable() {
      @Override
      public void run() {
        openPageNow();
      }
    }, millis, TimeUnit.MILLISECONDS);
  }

  private void openPageNow() {
    if (!isOutdated()) {
      JavaScriptDebuggerStarter.Util.startDebugOrLaunchBrowser(myRunConfiguration, mySettings);
    }
  }

  private boolean isOutdated() {
    return myOutdated.compute();
  }
}
