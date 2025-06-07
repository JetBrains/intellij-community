// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.settings.ShSettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.sh.shellcheck.ShShellcheckUtil.*;
import static com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY;

final class ShShellcheckTestUtil {
  private static final Logger LOG = Logger.getInstance(ShShellcheckTestUtil.class);

  static void downloadShellcheck(@NotNull Project project) throws IOException, InterruptedException {
    Path directory = getShellcheckTestDir();
    NioFiles.createDirectories(directory);

    final var localEel = EelProviderUtil.getLocalEel();

    Path shellcheck = directory.resolve(spellcheckBin(localEel.getPlatform()));
    if (Files.exists(shellcheck)) {
      String path = ShSettings.getShellcheckPath(project);
      String shellcheckPath = shellcheck.toString();
      if (path.equals(shellcheckPath)) {
        LOG.debug("Shellcheck already downloaded");
      }
      else {
        ShSettings.setShellcheckPath(project, shellcheckPath);
      }
      return;
    }

    String link = getShellcheckDistributionLink(localEel.getPlatform());
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(link)).build();
    HttpResponse<Path> response = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .version(HttpClient.Version.HTTP_1_1)
      .build()
      .send(request, HttpResponse.BodyHandlers.ofFile(directory.resolve("shellcheck.tgz")));
    LOG.info("Getting " + link + ", status code: " + response.statusCode());
    Path archive = response.body();
    String path = decompressShellcheck(archive, directory, localEel.getPlatform());
    if (!path.isEmpty()) {
      NioFiles.setExecutable(Path.of(path));
      ShSettings.setShellcheckPath(project, path);
    }
  }

  static Path getShellcheckTestDir() {
    return IS_UNDER_TEAMCITY ?
           Path.of(FileUtilRt.getTempDirectory(), ShLanguage.INSTANCE.getID()) :
           Path.of(PathManager.getTempPath(), "plugins", ShLanguage.INSTANCE.getID());
  }
}
