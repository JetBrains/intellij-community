// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.formatter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.sh.ShLanguage;
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
  private static final String WINDOWS_EXTENSION = ".exe";
  private static final String MAC = "_darwin";
  private static final String LINUX = "_linux";
  private static final String FREE_BSD = "_freebsd";

  public static void download(@Nullable Project project, @NotNull CodeStyleSettings settings, @NotNull Runnable onSuccess, @NotNull Runnable onFailure) {
    File directory = new File(DOWNLOAD_PATH);
    if (!directory.exists()) {
      //noinspection ResultOfMethodCallIgnored
      directory.mkdirs();
    }

    File formatter = new File(DOWNLOAD_PATH + File.separator + SHFMT + (SystemInfo.isWindows ? WINDOWS_EXTENSION : ""));
    if (formatter.exists()) {
      try {
        String formatterPath = formatter.getCanonicalPath();
        if (ShSettings.getShfmtPath().equals(formatterPath)) {
          LOG.debug("Shfmt formatter already downloaded");
        }
        else {
          ShSettings.setShfmtPath(formatterPath);
        }
        ApplicationManager.getApplication().invokeLater(onSuccess);
        return;
      }
      catch (IOException e) {
        LOG.debug("Can't evaluate formatter path", e);
        ApplicationManager.getApplication().invokeLater(onFailure);
        return;
      }
    }

    String downloadName = SHFMT + (SystemInfo.isWindows ? WINDOWS_EXTENSION : "");
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(getShfmtDistributionLink(), downloadName);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), downloadName);

    Task.Backgroundable task = new Task.Backgroundable(project, "Download Shfmt Formatter") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<Pair<File, DownloadableFileDescription>> pairs = downloader.download(new File(DOWNLOAD_PATH));
          Pair<File, DownloadableFileDescription> first = ContainerUtil.getFirstItem(pairs);
          File file = first != null ? first.first : null;
          if (file != null) {
            FileUtil.setExecutable(file);
            ShSettings.setShfmtPath(file.getCanonicalPath());
            ApplicationManager.getApplication().invokeLater(onSuccess);
            ShFeatureUsagesCollector.logFeatureUsage(FEATURE_ACTION_ID);
          }
        }
        catch (IOException e) {
          LOG.warn("Can't download shfmt formatter", e);
          ApplicationManager.getApplication().invokeLater(onFailure);
        }
      }
    };
    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  public static boolean isValidPath(@Nullable String path) {
    if (path == null) return false;
    if (ShSettings.I_DO_MIND.equals(path)) return true;
    File file = new File(path);
    if (!file.canExecute()) return false;
    return file.getName().contains(SHFMT);
  }

  @NotNull
  private static String getShfmtDistributionLink() {
    StringBuilder baseUrl = new StringBuilder("https://github.com/mvdan/sh/releases/download/")
        .append(SHFMT_VERSION)
        .append('/')
        .append(SHFMT)
        .append('_')
        .append(SHFMT_VERSION);

    if (SystemInfo.isMac) {
      baseUrl.append(MAC);
    }
    if (SystemInfo.isLinux) {
      baseUrl.append(LINUX);
    }
    if (SystemInfo.isWindows) {
      baseUrl.append(WINDOWS);
    }
    if (SystemInfo.isFreeBSD) {
      baseUrl.append(FREE_BSD);
    }
    if (SystemInfo.is64Bit) {
      baseUrl.append(ARCH_x86_64);
    }
    else {
      baseUrl.append(ARCH_i386);
    }
    if (SystemInfo.isWindows) {
      baseUrl.append(WINDOWS_EXTENSION);
    }
    return baseUrl.toString();
  }
}