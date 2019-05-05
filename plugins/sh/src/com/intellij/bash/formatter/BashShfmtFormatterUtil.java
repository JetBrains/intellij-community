package com.intellij.bash.formatter;

import com.intellij.bash.BashLanguage;
import com.intellij.bash.codeStyle.BashCodeStyleSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BashShfmtFormatterUtil {
  private static final Logger LOG = Logger.getInstance(BashShfmtFormatterUtil.class);
  private static final String APP_NAME = "shfmt";
  private static final String APP_VERSION = "v2.6.4";
  private static final String APP_PATH = PathManager.getPluginsPath() + File.separator + BashLanguage.INSTANCE.getID();

  private static final String ARCH_i386 = "_386";
  private static final String ARCH_x86_64 = "_amd64";
  private static final String WINDOWS = "_windows";
  private static final String MAC = "_darwin";
  private static final String LINUX = "_linux";
  private static final String FREE_BSD = "_freebsd";

  public static void download(@Nullable Project project, @NotNull CodeStyleSettings settings, @Nullable JComponent parent) {
    File directory = new File(APP_PATH);
    if (!directory.exists()) {
      directory.mkdirs();
    }

    BashCodeStyleSettings bashSettings = settings.getCustomSettings(BashCodeStyleSettings.class);
    File formatter = new File(APP_PATH + File.separator + APP_NAME);
    if (formatter.exists()) {
      try {
        String formatterPath = formatter.getCanonicalPath();
        if (bashSettings.SHFMT_PATH.equals(formatterPath)) {
          LOG.debug("Shfmt formatter already downloaded");
        }
        else {
          bashSettings.SHFMT_PATH = formatterPath;
          showInfoNotification();
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
    DownloadableFileDescription description = service.createFileDescription(getShfmtDistributionLink(), APP_NAME);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), APP_NAME);
    try {
      List<VirtualFile> virtualFiles = downloader.downloadFilesWithProgress(APP_PATH, project, parent);
      if (virtualFiles != null && virtualFiles.size() == 1) {
        String path = virtualFiles.get(0).getCanonicalPath();
        if (path != null) {
          FileUtilRt.setExecutableAttribute(path, true);
          bashSettings.SHFMT_PATH = path;
          showInfoNotification();
        }
      }
    }
    catch (IOException e) {
      LOG.warn("Can't download shfmt formatter", e);
      showErrorNotification();
    }
  }

  public static boolean isValidPath(@NotNull String path) {
    if (!new File(path).exists()) return false;

    try {
      GeneralCommandLine commandLine = new GeneralCommandLine().withExePath(path).withParameters("-version");
      ProcessOutput processOutput = ExecUtil.execAndGetOutput(commandLine, 3000);

      return processOutput.getStdout().startsWith(BashShfmtFormatterUtil.APP_VERSION);
    }
    catch (ExecutionException e) {
      LOG.debug("Exception in process execution", e);
    }
    return false;
  }

  @NotNull
  private static String getShfmtDistributionLink() {
    StringBuilder baseUrl = new StringBuilder("https://github.com/mvdan/sh/releases/download/")
        .append(APP_VERSION)
        .append("/")
        .append(APP_NAME)
        .append("_")
        .append(APP_VERSION);

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
    Notifications.Bus.notify(new Notification("Shell Script", "", "Can't download bash shfmt formatter. Please install in manually",
        NotificationType.ERROR));
  }
}
