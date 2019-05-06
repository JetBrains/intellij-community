package com.intellij.sh.shellcheck;

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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.sh.ShLanguage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.Decompressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

class ShShellcheckUtil {
  private static final Logger LOG = Logger.getInstance(ShShellcheckUtil.class);
  private static final String APP_NAME = "shellcheck";
  private static final String APP_PATH = PathManager.getPluginsPath() + File.separator + ShLanguage.INSTANCE.getID();
  @SuppressWarnings("UnresolvedPropertyKey")
  private static final String REGISTRY_KEY = "sh.shellcheck.path";

  static void download(@Nullable Project project, @Nullable JComponent parent) {
    File directory = new File(APP_PATH);
    if (!directory.exists()) {
      directory.mkdirs();
    }

    File shellcheck = new File(APP_PATH + File.separator + APP_NAME);
    if (shellcheck.exists()) {
      try {
        String path = getShellcheckPath();
        String shellcheckPath = shellcheck.getCanonicalPath();
        if (StringUtil.isNotEmpty(path) && path.equals(shellcheckPath)) {
          LOG.debug("Shellcheck already downloaded");
        }
        else {
          setShellcheckPath(shellcheckPath);
          showInfoNotification();
        }
        return;
      }
      catch (IOException e) {
        LOG.debug("Can't evaluate shellcheck path", e);
        showErrorNotification();
        return;
      }
    }

    String url = getShellcheckDistributionLink();
    if (StringUtil.isEmpty(url)) {
      LOG.debug("Unsupported OS for shellcheck");
      return;
    }

    String downloadName = APP_NAME;
    if (SystemInfoRt.isMac) {
      downloadName += "Archive";
    }
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(url, downloadName);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), downloadName);
    try {
      List<VirtualFile> virtualFiles = downloader.downloadFilesWithProgress(APP_PATH, project, parent);
      VirtualFile file = ContainerUtil.getFirstItem(virtualFiles);
      if (file != null) {
        String path = file.getCanonicalPath();
        if (path != null) {
          if (SystemInfoRt.isMac) {
            path = decompressShellcheck(path, directory);
          }
          if (StringUtil.isNotEmpty(path)) {
            FileUtilRt.setExecutableAttribute(path, true);
            setShellcheckPath(path);
            showInfoNotification();
          }
        }
      }
    }
    catch (IOException e) {
      LOG.warn("Can't download shellcheck", e);
      showErrorNotification();
    }
  }

  static boolean isValidPath(@NotNull String path) {
    if (!new File(path).exists()) return false;

    try {
      GeneralCommandLine commandLine = new GeneralCommandLine().withExePath(path).withParameters("--version");
      ProcessOutput processOutput = ExecUtil.execAndGetOutput(commandLine, 3000);

      return processOutput.getStdout().startsWith("ShellCheck");
    }
    catch (ExecutionException e) {
      LOG.debug("Exception in process execution", e);
    }
    return false;
  }

  @NotNull
  static String getShellcheckPath() {
    return Registry.stringValue(REGISTRY_KEY);
  }

  static void setShellcheckPath(@NotNull String path) {
    Registry.get(REGISTRY_KEY).setValue(path);
  }

  @NotNull
  private static String decompressShellcheck(@NotNull String tarPath, File directory) throws IOException {
    File archive = new File(tarPath);

    Decompressor.Tar tar = new Decompressor.Tar(archive);
    File tmpDir = new File(directory, "tmp");
    tar.filter(tarEntry -> tarEntry.equals("shellcheck/0.6.0_1/bin/shellcheck"));
    tar.postprocessor(outputFile -> {
      try {
        FileUtil.copyDir(outputFile.getParentFile(), directory);
      }
      catch (IOException e) {
        LOG.warn("Can't decompressor shellcheck", e);
      }
    });
    tar.extract(tmpDir);

    //Cleaning tmp dir and archive
    FileUtil.delete(tmpDir);
    FileUtil.delete(archive);

    File shellcheck = new File(APP_PATH + File.separator + APP_NAME);
    return shellcheck.exists() ? shellcheck.getCanonicalPath() : "";
  }

  @Nullable
  private static String getShellcheckDistributionLink() {
    if (SystemInfoRt.isMac) {
      return "https://homebrew.bintray.com/bottles/shellcheck-0.6.0_1.mojave.bottle.tar.gz";
    }
    if (SystemInfoRt.isLinux) {
      return "https://shellcheck.storage.googleapis.com/shellcheck-v0.6.0.linux-x86_64";
    }
    if (SystemInfoRt.isWindows) {
      return "https://shellcheck.storage.googleapis.com/shellcheck-v0.6.0.exe";
    }
    return null;
  }

  private static void showInfoNotification() {
    Notifications.Bus.notify(new Notification("Shell Script", "", "Shellcheck was successfully installed",
        NotificationType.INFORMATION));
  }

  private static void showErrorNotification() {
    Notifications.Bus.notify(new Notification("Shell Script", "", "Can't download sh shellcheck. Please install in manually",
        NotificationType.ERROR));
  }
}
