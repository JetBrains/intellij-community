// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyPIPackageCache extends PyAbstractPackageCache {
  private static final String CACHE_FILE_NAME = "pypi-cache.json";

  private static PyPIPackageCache ourInstance;

  @NotNull
  public static synchronized PyPIPackageCache getInstance() {
    return getInstance(getDefaultCachePath(CACHE_FILE_NAME));
  }

  @VisibleForTesting
  @NotNull
  static synchronized PyPIPackageCache getInstance(@NotNull Path pathToCache) {
    if (ourInstance == null) {
      ourInstance = PyAbstractPackageCache.load(PyPIPackageCache.class, new PyPIPackageCache(), pathToCache);
    }
    return ourInstance;
  }

  @NotNull
  public static synchronized PyPIPackageCache reload(@NotNull List<String> packageNames) {
    ourInstance = new PyPIPackageCache(packageNames);
    store(ourInstance, CACHE_FILE_NAME);
    return ourInstance;
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
