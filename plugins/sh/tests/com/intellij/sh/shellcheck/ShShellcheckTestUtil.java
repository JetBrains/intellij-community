// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.settings.ShSettings;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.sh.shellcheck.ShShellcheckUtil.*;
import static com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY;

class ShShellcheckTestUtil {
  private static final Logger LOG = Logger.getInstance(ShShellcheckTestUtil.class);

  static void downloadShellcheck() throws IOException, InterruptedException {
    Path directory = getShellcheckTestDir();
    NioFiles.createDirectories(directory);

    Path shellcheck = directory.resolve(SHELLCHECK_BIN);
    if (Files.exists(shellcheck)) {
      String path = ShSettings.getShellcheckPath();
      String shellcheckPath = shellcheck.toString();
      if (path.equals(shellcheckPath)) {
        LOG.debug("Shellcheck already downloaded");
      }
      else {
        ShSettings.setShellcheckPath(shellcheckPath);
      }
      return;
    }

    String link = getShellcheckDistributionLink();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(link)).build();
    HttpResponse<Path> response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
      .send(request, HttpResponse.BodyHandlers.ofFile(directory.resolve("shellcheck.tgz")));
    LOG.info("Getting " + link + ", status code: " + response.statusCode());
    Path archive = response.body();
    String path = decompressShellcheck(archive.toFile(), directory.toFile());
    if (!path.isEmpty()) {
      NioFiles.setExecutable(Path.of(path));
      ShSettings.setShellcheckPath(path);
    }
  }

  static Path getShellcheckTestDir() {
    return IS_UNDER_TEAMCITY ?
           Path.of(FileUtil.getTempDirectory(), ShLanguage.INSTANCE.getID()) :
           Path.of(PathManager.getTempPath(), "plugins", ShLanguage.INSTANCE.getID());
  }
}
