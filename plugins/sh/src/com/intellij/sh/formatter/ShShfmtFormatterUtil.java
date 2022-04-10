// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.formatter;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.ShNotificationDisplayIds;
import com.intellij.sh.settings.ShSettings;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.intellij.sh.ShBundle.message;
import static com.intellij.sh.ShBundle.messagePointer;
import static com.intellij.sh.ShLanguage.NOTIFICATION_GROUP;
import static com.intellij.sh.ShLanguage.NOTIFICATION_GROUP_ID;
import static com.intellij.sh.statistics.ShCounterUsagesCollector.EXTERNAL_FORMATTER_DOWNLOADED_EVENT_ID;

public final class ShShfmtFormatterUtil {
  private static final Logger LOG = Logger.getInstance(ShShfmtFormatterUtil.class);
  private static final Key<Boolean> UPDATE_NOTIFICATION_SHOWN = Key.create("SHFMT_UPDATE");

  private static final @NlsSafe String SHFMT = "shfmt";
  private static final @NlsSafe String OLD_SHFMT = "old_shfmt";
  private static final @NlsSafe String SHFMT_VERSION = "v3.3.1";
  private static final @NlsSafe String DOWNLOAD_PATH = PathManager.getPluginsPath() + File.separator + ShLanguage.INSTANCE.getID();

  private static final @NlsSafe String ARCH_i386 = "_386";
  private static final @NlsSafe String ARCH_x86_64 = "_amd64";
  private static final @NlsSafe String ARCH_ARM64 = "_arm64";
  private static final @NlsSafe String WINDOWS = "_windows";
  private static final @NlsSafe String WINDOWS_EXTENSION = ".exe";
  private static final @NlsSafe String MAC = "_darwin";
  private static final @NlsSafe String LINUX = "_linux";
  private static final @NlsSafe String FREE_BSD = "_freebsd";

  public static void download(@Nullable Project project, @NotNull Runnable onSuccess, @NotNull Runnable onFailure) {
    download(project, onSuccess, onFailure, false);
  }

  private static void download(@Nullable Project project, @NotNull Runnable onSuccess, @NotNull Runnable onFailure, boolean withReplace) {
    File directory = new File(DOWNLOAD_PATH);
    if (!directory.exists()) {
      //noinspection ResultOfMethodCallIgnored
      directory.mkdirs();
    }

    File formatter = new File(DOWNLOAD_PATH + File.separator + SHFMT + (SystemInfo.isWindows ? WINDOWS_EXTENSION : ""));
    File oldFormatter = new File(DOWNLOAD_PATH + File.separator + OLD_SHFMT + (SystemInfo.isWindows ? WINDOWS_EXTENSION : ""));
    if (formatter.exists()) {
      if (withReplace) {
        boolean successful = renameOldFormatter(formatter, oldFormatter, onFailure);
        if (!successful) return;
      } else {
        setupFormatterPath(formatter, onSuccess, onFailure);
        return;
      }
    }

    String downloadName = SHFMT + (SystemInfo.isWindows ? WINDOWS_EXTENSION : "");
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(getShfmtDistributionLink(), downloadName);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), downloadName);

    Task.Backgroundable task = new Task.Backgroundable(project, message("sh.label.download.shfmt.formatter")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<Pair<File, DownloadableFileDescription>> pairs = downloader.download(new File(DOWNLOAD_PATH));
          Pair<File, DownloadableFileDescription> first = ContainerUtil.getFirstItem(pairs);
          File file = first != null ? first.first : null;
          if (file != null) {
            FileUtil.setExecutable(file);
            ShSettings.setShfmtPath(file.getCanonicalPath());
            if (withReplace) {
              LOG.info("Remove old formatter");
              FileUtil.delete(oldFormatter);
            }
            ApplicationManager.getApplication().invokeLater(onSuccess);
            EXTERNAL_FORMATTER_DOWNLOADED_EVENT_ID.log();
          }
        }
        catch (IOException e) {
          LOG.warn("Can't download shfmt formatter", e);
          if (withReplace) rollbackToOldFormatter(formatter, oldFormatter);
          ApplicationManager.getApplication().invokeLater(onFailure);
        }
      }
    };
    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  private static void setupFormatterPath(@NotNull File formatter, @NotNull Runnable onSuccess, @NotNull Runnable onFailure) {
    try {
      String formatterPath = formatter.getCanonicalPath();
      if (ShSettings.getShfmtPath().equals(formatterPath)) {
        LOG.info("Shfmt formatter already downloaded");
      }
      else {
        ShSettings.setShfmtPath(formatterPath);
      }
      if (!formatter.canExecute()) FileUtil.setExecutable(formatter);
      ApplicationManager.getApplication().invokeLater(onSuccess);
    }
    catch (IOException e) {
      LOG.warn("Can't evaluate formatter path or make it executable", e);
      ApplicationManager.getApplication().invokeLater(onFailure);
    }
  }

  private static boolean renameOldFormatter(@NotNull File formatter, @NotNull File oldFormatter, @NotNull Runnable onFailure) {
    LOG.info("Rename formatter to the temporary filename");
    try {
      FileUtil.rename(formatter, oldFormatter);
    }
    catch (IOException e) {
      LOG.info("Can't rename formatter to the temporary filename", e);
      ApplicationManager.getApplication().invokeLater(onFailure);
      return false;
    }
    return true;
  }

  private static void rollbackToOldFormatter(@NotNull File formatter, @NotNull File oldFormatter) {
    LOG.info("Update failed, rollback");
    try {
      FileUtil.rename(oldFormatter, formatter);
    }
    catch (IOException e) {
      LOG.info("Can't rollback formatter after failed update", e);
    }
    FileUtil.delete(oldFormatter);
  }

  public static boolean isValidPath(@Nullable String path) {
    if (path == null) return false;
    if (ShSettings.I_DO_MIND_SUPPLIER.get().equals(path)) return true;
    File file = new File(path);
    if (!file.canExecute()) return false;
    return file.getName().contains(SHFMT);
  }

  static void checkShfmtForUpdate(@NotNull Project project) {
    Application application = ApplicationManager.getApplication();
    if (application.getUserData(UPDATE_NOTIFICATION_SHOWN) != null) return;
    application.putUserData(UPDATE_NOTIFICATION_SHOWN, true);

    if (application.isDispatchThread()) {
      application.executeOnPooledThread(() -> checkForUpdateInBackgroundThread(project));
    } else {
      checkForUpdateInBackgroundThread(project);
    }
  }

  private static void checkForUpdateInBackgroundThread(@NotNull Project project) {
    if (ApplicationManager.getApplication().isDispatchThread()) LOG.error("Must not be in event-dispatch thread");
    if (!isNewVersionAvailable()) return;
    Notification notification = NOTIFICATION_GROUP.createNotification(message("sh.shell.script"), message("sh.fmt.update.question"),
                                                 NotificationType.INFORMATION);
    notification.setSuggestionType(true);
    notification.setDisplayId(ShNotificationDisplayIds.UPDATE_FORMATTER);
    notification.addAction(
      NotificationAction.createSimple(messagePointer("sh.update"), () -> {
        notification.expire();
        download(project,
                 () -> Notifications.Bus
                   .notify(NOTIFICATION_GROUP.createNotification(message("sh.shell.script"), message("sh.fmt.success.update"),
                                            NotificationType.INFORMATION)
                             .setDisplayId(ShNotificationDisplayIds.UPDATE_FORMATTER_SUCCESS)),
                 () -> Notifications.Bus
                   .notify(NOTIFICATION_GROUP.createNotification(message("sh.shell.script"), message("sh.fmt.cannot.update"),
                                            NotificationType.ERROR)
                             .setDisplayId(ShNotificationDisplayIds.UPDATE_FORMATTER_ERROR)),
                 true);
      }));
    notification.addAction(NotificationAction.createSimple(messagePointer("sh.skip.version"), () -> {
      notification.expire();
      ShSettings.setSkippedShfmtVersion(SHFMT_VERSION);
    }));
    Notifications.Bus.notify(notification, project);
  }

  private static boolean isNewVersionAvailable() {
    String path = ShSettings.getShfmtPath();
    if (ShSettings.I_DO_MIND_SUPPLIER.get().equals(path)) return false;
    File file = new File(path);
    if (!file.canExecute()) return false;
    if (!file.getName().contains(SHFMT)) return false;
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine().withExePath(path).withParameters("--version");
      ProcessOutput processOutput = ExecUtil.execAndGetOutput(commandLine, 3000);

      String stdout = processOutput.getStdout();
      return !stdout.contains(SHFMT_VERSION) && !ShSettings.getSkippedShfmtVersion().equals(SHFMT_VERSION);
    }
    catch (ExecutionException e) {
      LOG.debug("Exception in process execution", e);
    }
    return false;
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
    else if (SystemInfo.isLinux) {
      baseUrl.append(LINUX);
    }
    else if (SystemInfo.isWindows) {
      baseUrl.append(WINDOWS);
    }
    else if (SystemInfo.isFreeBSD) {
      baseUrl.append(FREE_BSD);
    }

    if (CpuArch.isIntel64()) {
      baseUrl.append(ARCH_x86_64);
    }
    if (CpuArch.isIntel32()) {
      baseUrl.append(ARCH_i386);
    }
    else if (CpuArch.isArm64()) {
      baseUrl.append(ARCH_ARM64);
    }

    if (SystemInfo.isWindows) {
      baseUrl.append(WINDOWS_EXTENSION);
    }

    return baseUrl.toString();
  }
}
