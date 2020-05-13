// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers;

import com.google.common.net.HostAndPort;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Urls;
import com.intellij.util.net.NetUtils;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public class BrowserStarter {
  private static final Logger LOG = Logger.getInstance(BrowserStarter.class);

  private final StartBrowserSettings mySettings;
  private final RunConfiguration myRunConfiguration;
  private final BooleanSupplier myOutdated;

  public BrowserStarter(@NotNull RunConfiguration runConfiguration,
                        @NotNull StartBrowserSettings settings,
                        @NotNull BooleanSupplier outdated) {
    mySettings = settings;
    myRunConfiguration = runConfiguration;
    myOutdated = outdated;
  }

  public BrowserStarter(@NotNull RunConfiguration runConfiguration,
                        @NotNull StartBrowserSettings settings,
                        @NotNull ProcessHandler serverProcessHandler) {
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

  private void checkAndOpenPageLater(@NotNull HostAndPort hostAndPort, int attemptNumber, int delayMillis) {
    JobScheduler.getScheduler().schedule(() -> checkAndOpenPage(hostAndPort, attemptNumber), delayMillis, TimeUnit.MILLISECONDS);
  }

  private void checkAndOpenPage(@NotNull HostAndPort hostAndPort, int attemptNumber) {
    if (isOutdated()) {
      LOG.info("Opening " + hostAndPort + " aborted");
    }
    else if (NetUtils.canConnectToRemoteSocket(hostAndPort.getHost(), hostAndPort.getPort())) {
      openPageNow();
    }
    else if (attemptNumber < 100) {
      int delayMillis = getDelayMillis(attemptNumber);
      LOG.info("#" + attemptNumber + " check " + hostAndPort + " failed, scheduling next check in " + delayMillis + "ms");
      checkAndOpenPageLater(hostAndPort, attemptNumber + 1, delayMillis);
    }
    else {
      LOG.info("#" + attemptNumber + " check " + hostAndPort + " failed. Too many failed checks. Failed to open " + hostAndPort);
      showBrowserOpenTimeoutNotification();
    }
  }

  private static int getDelayMillis(int attemptNumber) {
    // [0 - 5 seconds] check each 500 ms
    if (attemptNumber < 10) {
      return 500;
    }
    // [5 - 25 seconds] check each 1000 ms
    if (attemptNumber < 20) {
      return 1000;
    }
    // [25 - 425 seconds] check each 5000 ms
    return 5000;
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
    return myOutdated.getAsBoolean();
  }

  private void showBrowserOpenTimeoutNotification() {
    NotificationGroup group =
      NotificationGroup.balloonGroup("URL does not respond notification", XmlBundle.message("browser.notification.timeout.group"));
    NotificationType type = NotificationType.ERROR;

    String title = XmlBundle.message("browser.notification.timeout.title");
    String url = Objects.requireNonNull(mySettings.getUrl());
    String openUrlDescription = "open_url";
    String content = XmlBundle.message("browser.notification.timeout.text", url, openUrlDescription);

    Notification openBrowserNotification = group.createNotification(title, content, type, (notification, event) -> {
      if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
      if (event.getDescription().equals(openUrlDescription)) {
        BrowserUtil.open(url);
        notification.expire();
      }
    });

    openBrowserNotification.notify(myRunConfiguration.getProject());
  }
}
