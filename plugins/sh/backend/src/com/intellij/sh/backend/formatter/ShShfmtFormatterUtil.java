// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.formatter;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.eel.EelPlatform;
import com.intellij.sh.ShNotificationDisplayIds;
import com.intellij.sh.settings.ShSettings;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.text.SemVer;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Locale;

import static com.intellij.platform.eel.EelPlatformKt.*;
import static com.intellij.platform.eel.provider.EelProviderUtil.getEelDescriptor;
import static com.intellij.platform.eel.provider.EelProviderUtil.toEelApiBlocking;
import static com.intellij.sh.ShBundle.message;
import static com.intellij.sh.ShBundle.messagePointer;
import static com.intellij.sh.ShNotification.NOTIFICATION_GROUP;
import static com.intellij.sh.statistics.ShCounterUsagesCollector.EXTERNAL_FORMATTER_DOWNLOADED_EVENT_ID;
import static com.intellij.sh.utils.ExternalServicesUtil.computeDownloadPath;

public final class ShShfmtFormatterUtil {
  private static final Logger LOG = Logger.getInstance(ShShfmtFormatterUtil.class);
  private static final Key<Boolean> UPDATE_NOTIFICATION_SHOWN = Key.create("SHFMT_UPDATE");

  private static final @NlsSafe String SHFMT = "shfmt";
  private static final @NlsSafe String OLD_SHFMT = "old_shfmt";
  private static final @NlsSafe String SHFMT_VERSION = "v3.3.1";

  private static final @NlsSafe String ARCH_i386 = "_386";
  private static final @NlsSafe String ARCH_x86_64 = "_amd64";
  private static final @NlsSafe String ARCH_ARM64 = "_arm64";
  private static final @NlsSafe String WINDOWS = "_windows";
  private static final @NlsSafe String WINDOWS_EXTENSION = ".exe";
  private static final @NlsSafe String MAC = "_darwin";
  private static final @NlsSafe String LINUX = "_linux";
  private static final @NlsSafe String FREE_BSD = "_freebsd";

  public static void download(@NotNull Project project, @NotNull Runnable onSuccess, @NotNull Runnable onFailure) {
    download(project, onSuccess, onFailure, false);
  }

  private static void download(@NotNull Project project, @NotNull Runnable onSuccess, @NotNull Runnable onFailure, boolean withReplace) {
    Task.Backgroundable task = new Task.Backgroundable(project, message("sh.label.download.shfmt.formatter")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final var eelDescriptor = getEelDescriptor(project);
        final var eel = toEelApiBlocking(eelDescriptor);
        final var eelPlatform = eel.getPlatform();

        final var downloadPath = computeDownloadPath(eel);

        if (!Files.exists(downloadPath)) {
          try {
            Files.createDirectory(downloadPath);
          }
          catch (IOException e) {
            //
          }
        }

        final var formatter = downloadPath.resolve(SHFMT + (isWindows(eelPlatform) ? WINDOWS_EXTENSION : ""));
        final var oldFormatter = downloadPath.resolve(OLD_SHFMT + (isWindows(eelPlatform) ? WINDOWS_EXTENSION : ""));

        if (Files.exists(formatter)) {
          if (withReplace) {
            boolean successful = renameOldFormatter(formatter.toFile(), oldFormatter.toFile(), onFailure);
            if (!successful) return;
          }
          else {
            setupFormatterPath(project, formatter.toFile(), onSuccess, onFailure);
            return;
          }
        }

        final var downloadName = SHFMT + (isWindows(eelPlatform) ? WINDOWS_EXTENSION : "");
        final var service = DownloadableFileService.getInstance();
        final var description = service.createFileDescription(getShfmtDistributionLink(eel.getPlatform()), downloadName);
        final var downloader = service.createDownloader(Collections.singletonList(description), downloadName);

        try {
          final var pairs = downloader.download(downloadPath.toFile());
          final var first = ContainerUtil.getFirstItem(pairs);
          final var file = first != null ? first.first : null;
          if (file != null) {
            FileUtil.setExecutable(file);
            ShSettings.setShfmtPath(project, file.getCanonicalPath());
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
          if (withReplace) rollbackToOldFormatter(formatter.toFile(), oldFormatter.toFile());
          ApplicationManager.getApplication().invokeLater(onFailure);
        }
      }
    };
    final var processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  private static void setupFormatterPath(@NotNull Project project, @NotNull File formatter, @NotNull Runnable onSuccess, @NotNull Runnable onFailure) {
    try {
      String formatterPath = formatter.getCanonicalPath();
      if (ShSettings.getShfmtPath(project).equals(formatterPath)) {
        LOG.info("Shfmt formatter already downloaded");
      }
      else {
        ShSettings.setShfmtPath(project, formatterPath);
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
    }
    else {
      checkForUpdateInBackgroundThread(project);
    }
  }

  private static void checkForUpdateInBackgroundThread(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    Pair<String, String> newVersionAvailable = getVersionUpdate(project);
    if (newVersionAvailable == null) return;

    String currentVersion = newVersionAvailable.first;
    String newVersion = newVersionAvailable.second;

    Notification notification = NOTIFICATION_GROUP.createNotification(
      message("sh.shell.script"),
      message("sh.fmt.update.question", currentVersion, newVersion),
      NotificationType.INFORMATION);
    notification.setSuggestionType(true);
    notification.setDisplayId(ShNotificationDisplayIds.UPDATE_FORMATTER);
    notification.addAction(
      NotificationAction.createSimple(messagePointer("sh.update"), () -> {
        notification.expire();
        download(project,
                 () -> NOTIFICATION_GROUP.createNotification(message("sh.shell.script"), message("sh.fmt.success.update"),
                                                             NotificationType.INFORMATION)
                   .setDisplayId(ShNotificationDisplayIds.UPDATE_FORMATTER_SUCCESS).notify(project),
                 () -> NOTIFICATION_GROUP.createNotification(message("sh.shell.script"), message("sh.fmt.cannot.update"),
                                                             NotificationType.ERROR)
                   .setDisplayId(ShNotificationDisplayIds.UPDATE_FORMATTER_ERROR).notify(project),
                 true);
      }));
    notification.addAction(NotificationAction.createSimple(messagePointer("sh.skip.version"), () -> {
      notification.expire();
      ShSettings.setSkippedShfmtVersion(SHFMT_VERSION);
    }));
    notification.notify(project);
  }

  /**
   * @return pair of old and new versions or null if there's no update
   */
  private static Pair<String, String> getVersionUpdate(@NotNull Project project) {
    final String updateVersion = StringsKt.removePrefix(SHFMT_VERSION, "v");
    final SemVer updateVersionVer = SemVer.parseFromText(updateVersion);
    if (updateVersionVer == null) return null;
    if (ShSettings.getSkippedShfmtVersion().equals(updateVersion)) return null;

    String path = ShSettings.getShfmtPath(project);
    if (ShSettings.I_DO_MIND_SUPPLIER.get().equals(path)) return null;
    File file = new File(path);
    if (!file.canExecute()) return null;
    if (!file.getName().contains(SHFMT)) return null;
    try {
      GeneralCommandLine commandLine = new GeneralCommandLine().withExePath(path).withParameters("--version");
      ProcessOutput processOutput = ExecUtil.execAndGetOutput(commandLine, 3000);

      String stdout = processOutput.getStdout();
      String current = getVersionFromStdOut(stdout);
      if (current == null) {
        current = "unknown";
        return Pair.create(current, updateVersion);
      }
      SemVer currentVersion = SemVer.parseFromText(current);
      if (currentVersion == null || updateVersionVer.isGreaterThan(currentVersion)) {
        return Pair.create(current, updateVersion);
      }
      return null;
    }
    catch (ExecutionException e) {
      LOG.debug("Exception in process execution", e);
    }
    return null;
  }

  private static String getVersionFromStdOut(String stdout) {
    String[] lines = StringUtil.splitByLines(stdout);
    for (String line : lines) {
      line = line.trim().toLowerCase(Locale.ENGLISH);
      if (line.isEmpty()) continue;
      if (Character.isDigit(line.charAt(0))) return line;
    }
    return null;
  }

  private static @NotNull String getShfmtDistributionLink(@NotNull EelPlatform platform) {
    StringBuilder baseUrl = new StringBuilder("https://github.com/mvdan/sh/releases/download/")
      .append(SHFMT_VERSION)
      .append('/')
      .append(SHFMT)
      .append('_')
      .append(SHFMT_VERSION);

    if (isMac(platform)) {
      baseUrl.append(MAC);
    }
    else if (isLinux(platform)) {
      baseUrl.append(LINUX);
    }
    else if (isWindows(platform)) {
      baseUrl.append(WINDOWS);
    }
    else if (isFreeBSD(platform)) {
      baseUrl.append(FREE_BSD);
    }

    if (isX86_64(platform)) {
      baseUrl.append(ARCH_x86_64);
    }
    if (isX86(platform)) {
      baseUrl.append(ARCH_i386);
    }
    else if (isArm64(platform)) {
      baseUrl.append(ARCH_ARM64);
    }

    if (isWindows(platform)) {
      baseUrl.append(WINDOWS_EXTENSION);
    }

    return baseUrl.toString();
  }
}
