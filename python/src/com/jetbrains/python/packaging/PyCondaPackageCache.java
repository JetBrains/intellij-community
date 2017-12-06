// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author Mikhail Golubev
 */
public class PyCondaPackageCache extends PyAbstractPackageCache {
  private static final String CACHE_FILE_NAME = "conda-cache.json";

  private static PyCondaPackageCache ourInstance;

  @NotNull
  public static synchronized PyCondaPackageCache getInstance() {
    if (ourInstance == null) {
      ourInstance = load(PyCondaPackageCache.class, new PyCondaPackageCache(), CACHE_FILE_NAME);
    }
    return ourInstance;
  }

  @NotNull
  public static synchronized PyCondaPackageCache reload(@NotNull Map<String, List<String>> packageNames) {
    ourInstance = new PyCondaPackageCache(packageNames);
    store(ourInstance, CACHE_FILE_NAME);
    return ourInstance;
  }

  private PyCondaPackageCache() {
  }

  private PyCondaPackageCache(@NotNull Map<String, List<String>> nameToVersion) {
    for (Map.Entry<String, List<String>> entry : nameToVersion.entrySet()) {
      myPackages.put(entry.getKey(), new PyAbstractPackageCache.PackageInfo(entry.getValue()));
    }
  }
}
