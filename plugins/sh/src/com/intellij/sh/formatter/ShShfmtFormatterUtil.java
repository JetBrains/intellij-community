// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.formatter;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.codeStyle.ShCodeStyleSettings;
import com.intellij.sh.settings.ShSettings;
import com.intellij.sh.statistics.ShFeatureUsagesCollector;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ShShfmtFormatterUtil {
  private static final Logger LOG = Logger.getInstance(ShShfmtFormatterUtil.class);
  private static final String FEATURE_ACTION_ID = "ExternalFormatterDownloaded";

  private static final String SHFMT = "shfmt";
  private static final String SHFMT_VERSION = "v2.6.4";
  private static final String DOWNLOAD_PATH = PathManager.getPluginsPath() + File.separator + ShLanguage.INSTANCE.getID();

  private static final String ARCH_i386 = "_386";
  private static final String ARCH_x86_64 = "_amd64";
  private static final String WINDOWS = "_windows";
  private static final String MAC = "_darwin";
  private static final String LINUX = "_linux";
  private static final String FREE_BSD = "_freebsd";

  public static void download(@Nullable Project project, @NotNull CodeStyleSettings settings, @Nullable Runnable onSuccess) {
    File directory = new File(DOWNLOAD_PATH);
    if (!directory.exists()) {
      //noinspection ResultOfMethodCallIgnored
      directory.mkdirs();
    }

    ShCodeStyleSettings shSettings = settings.getCustomSettings(ShCodeStyleSettings.class);
    File formatter = new File(DOWNLOAD_PATH + File.separator + SHFMT);
    if (formatter.exists()) {
      try {
        String formatterPath = formatter.getCanonicalPath();
        if (shSettings.SHFMT_PATH.equals(formatterPath)) {
          LOG.debug("Shfmt formatter already downloaded");
        }
        else {
          shSettings.SHFMT_PATH = formatterPath;
          showInfoNotification();
        }
        if (onSuccess != null) {
          ApplicationManager.getApplication().invokeLater(onSuccess);
        }
        return;
      }
      catch (IOException e) {
        LOG.debug("Can't evaluate formatter path", e);
        showErrorNotification();
        return;
      }
    }

    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(getShfmtDistributionLink(), SHFMT);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), SHFMT);

    Task.Backgroundable task = new Task.Backgroundable(project, "Download Shfmt Formatter") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<Pair<File, DownloadableFileDescription>> pairs = downloader.download(new File(DOWNLOAD_PATH));
          Pair<File, DownloadableFileDescription> first = ContainerUtil.getFirstItem(pairs);
          File file = first != null ? first.first : null;
          if (file != null) {
            String path = file.getCanonicalPath();
            FileUtilRt.setExecutableAttribute(path, true);
            shSettings.SHFMT_PATH = path;
            showInfoNotification();
            if (onSuccess != null) {
              ApplicationManager.getApplication().invokeLater(onSuccess);
            }
            ShFeatureUsagesCollector.logFeatureUsage(FEATURE_ACTION_ID);
          }
        }
        catch (IOException e) {
          LOG.warn("Can't download shfmt formatter", e);
          showErrorNotification();
        }
      }
    };
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
  }

  public static boolean isValidPath(@Nullable String path) {
    if (path == null) return false;
    if (ShSettings.I_DO_MIND.equals(path)) return true;
    File file = new File(path);
    if (!file.canExecute()) return false;
    return file.getName().contains("shfmt");

//    try {
//      GeneralCommandLine commandLine = new GeneralCommandLine().withExePath(path).withParameters("-version");
//      ProcessOutput processOutput = ExecUtil.execAndGetOutput(commandLine, 3000);
//
//      return processOutput.getStdout().startsWith(ShShfmtFormatterUtil.SHFMT_VERSION);
//    }
//    catch (ExecutionException e) {
//      LOG.debug("Exception in process execution", e);
//    }
//    return false;
  }

  @NotNull
  private static String getShfmtDistributionLink() {
    StringBuilder baseUrl = new StringBuilder("https://github.com/mvdan/sh/releases/download/")
        .append(SHFMT_VERSION)
        .append("/")
        .append(SHFMT)
        .append("_")
        .append(SHFMT_VERSION);

    if (SystemInfoRt.isMac) {
      baseUrl.append(MAC);
    }
    if (SystemInfoRt.isLinux) {
      baseUrl.append(LINUX);
    }
    if (SystemInfoRt.isWindows) {
      baseUrl.append(WINDOWS);
    }
    if (SystemInfoRt.isFreeBSD) {
      baseUrl.append(FREE_BSD);
    }
    if (SystemInfoRt.is64Bit) {
      baseUrl.append(ARCH_x86_64);
    }
    else {
      baseUrl.append(ARCH_i386);
    }
    return baseUrl.toString();
  }

  private static void showInfoNotification() {
    Notifications.Bus.notify(new Notification("Shell Script", "", "Shell script formatter was successfully installed",
        NotificationType.INFORMATION));
  }

  private static void showErrorNotification() {
    Notifications.Bus.notify(new Notification("Shell Script", "", "Can't download sh shfmt formatter. Please install it manually",
        NotificationType.ERROR));
  }
}
