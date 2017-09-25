/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    this(runConfiguration, settings, () -> serverProcessHandler.isProcessTerminating() || serverProcessHandler.isProcessTerminated());
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
      openPageLater();
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
    JobScheduler.getScheduler().schedule(() -> checkAndOpenPage(hostAndPort, attemptNumber), delayMillis, TimeUnit.MILLISECONDS);
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

  private void openPageLater() {
    JobScheduler.getScheduler().schedule(() -> openPageNow(), 1000, TimeUnit.MILLISECONDS);
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
