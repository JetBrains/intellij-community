// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

/**
 * PyPI cache updater
 */
final class PyPackagesUpdater implements StartupActivity, DumbAware {
  private static final Logger LOG = Logger.getInstance(PyPackagesUpdater.class);
  private static final Duration EXPIRATION_TIMEOUT = Duration.ofDays(1);

  @Override
  public void runActivity(@NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode() || Registry.is("disable.python.cache.update") || !checkNeeded()) return;

    try {
      PyPIPackageUtil.INSTANCE.updatePyPICache();
    }
    catch (IOException e) {
      LOG.warn(e.getMessage());
    }
  }

  private static boolean checkNeeded() {
    PyPackageService service = PyPackageService.getInstance();
    if (service.PYPI_REMOVED) return false;
    try {
      FileTime fileMTime = Files.getLastModifiedTime(PyPIPackageCache.getDefaultCachePath());
      if (fileMTime.toInstant().plus(EXPIRATION_TIMEOUT).isAfter(Instant.now())) {
        return false;
      }
    }
    catch (NoSuchFileException ignored) {
      // Non-existent cache file should be rebuilt
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    LOG.debug("Updating outdated PyPI package cache");
    return true;
  }
}
