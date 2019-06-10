// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.settings.ShSettings;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.intellij.sh.shellcheck.ShShellcheckUtil.*;
import static com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY;

class ShShellcheckTestUtil {
  protected static final Logger LOG = Logger.getInstance(ShShellcheckTestUtil.class);

  private static final String CACHE_SHELLCHECK_URL = "https://cache-redirector.jetbrains.com/jetbrains.bintray.com/" +
                                                     "intellij-third-party-dependencies/" +
                                                     "org/jetbrains/intellij/deps/shellcheck/";

  static void downloadShellcheck() {
    String downloadPath = getShellcheckTestDir();
    File directory = new File(downloadPath);
    if (!directory.exists()) {
      //noinspection ResultOfMethodCallIgnored
      directory.mkdirs();
    }

    File shellcheck = new File(downloadPath + File.separator + SHELLCHECK);
    if (shellcheck.exists()) {
      try {
        String path = ShSettings.getShellcheckPath();
        String shellcheckPath = shellcheck.getCanonicalPath();
        if (StringUtil.isNotEmpty(path) && path.equals(shellcheckPath)) {
          LOG.debug("Shellcheck already downloaded");
        }
        else {
          ShSettings.setShellcheckPath(shellcheckPath);
        }
        return;
      }
      catch (IOException e) {
        LOG.debug("Can't evaluate shellcheck path", e);
        return;
      }
    }

    String downloadName = SHELLCHECK + SHELLCHECK_ARCHIVE_EXTENSION;
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(getShellcheckDistributionLink(), downloadName);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), downloadName);

    try {
      List<Pair<File, DownloadableFileDescription>> pairs = downloader.download(new File(downloadPath));
      Pair<File, DownloadableFileDescription> first = ContainerUtil.getFirstItem(pairs);
      File file = first != null ? first.first : null;
      if (file != null) {
        String path = decompressShellcheck(file.getCanonicalPath(), directory);
        if (StringUtil.isNotEmpty(path)) {
          FileUtil.setExecutable(new File(path));
          ShSettings.setShellcheckPath(path);
        }
      }
    }
    catch (IOException e) {
      LOG.error("Can't download shellcheck", e);
    }
  }

  private static String getShellcheckDistributionLink() {
    String platform = SystemInfo.isMac ? "mac" : SystemInfo.isWindows ? "windows" : "linux";
    String shellcheckUrl = IS_UNDER_TEAMCITY ? CACHE_SHELLCHECK_URL : SHELLCHECK_URL;
    return shellcheckUrl + SHELLCHECK_VERSION + "/" + platform + SHELLCHECK_ARCHIVE_EXTENSION;
  }

  @NotNull
  static String getShellcheckTestDir() {
    return IS_UNDER_TEAMCITY ?
           FileUtil.getTempDirectory() + File.separator + ShLanguage.INSTANCE.getID() :
           PathManager.getTempPath() + File.separator + "plugins" + File.separator + ShLanguage.INSTANCE.getID();
  }
}
