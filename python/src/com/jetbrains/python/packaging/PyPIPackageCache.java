// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;
import java.util.List;

/**
 * @author Mikhail Golubev
 * @deprecated use {@link com.jetbrains.python.packaging.pip.PypiPackageCache} to search for PyPI packages
 * or {@link com.jetbrains.python.packaging.management.PythonRepositoryManager#allPackages()} to search for all available packages.
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public class PyPIPackageCache extends PyAbstractPackageCache {
  private static final String CACHE_FILE_NAME = "pypi-cache.json";

  private static PyPIPackageCache ourInstance;

  public static synchronized @NotNull PyPIPackageCache getInstance() {
    return getInstance(getDefaultCachePath(CACHE_FILE_NAME));
  }

  public static @NotNull Path getDefaultCachePath() {
    return getDefaultCachePath(CACHE_FILE_NAME);
  }

  @VisibleForTesting
  public static synchronized @NotNull PyPIPackageCache getInstance(@NotNull Path pathToCache) {
    if (ourInstance == null) {
      ourInstance = PyAbstractPackageCache.load(PyPIPackageCache.class, new PyPIPackageCache(), pathToCache);
    }
    return ourInstance;
  }

  public static synchronized @NotNull PyPIPackageCache reload(@NotNull List<String> packageNames) {
    ourInstance = new PyPIPackageCache(packageNames);
    store(ourInstance, CACHE_FILE_NAME);
    return ourInstance;
  }

  @TestOnly
  public static synchronized void reset() {
    ourInstance = null;
  }

  private PyPIPackageCache() {
  }

  public PyPIPackageCache(@NotNull List<String> packageNames) {
    for (String name : packageNames) {
      // Don't save null as a value, since GSON excludes such entries from serialization,
      // unless GsonBuilder#serializeNulls() is used, but then object fields with null values
      // are saved as well.
      myPackages.put(name, PackageInfo.EMPTY);
    }
  }
}
