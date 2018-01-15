// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author Mikhail Golubev
 */
public class PyCondaPackageCache extends PyAbstractPackageCache {
  private static final String CACHE_FILE_NAME = "conda-cache.json";

  private static PyCondaPackageCache ourInstance;

  @NotNull
  public static synchronized PyCondaPackageCache getInstance() {
    if (ourInstance == null) {
      ourInstance = load(PyCondaPackageCache.class, new PyCondaPackageCache(), getDefaultCachePath(CACHE_FILE_NAME));
    }
    return ourInstance;
  }

  @NotNull
  public static synchronized PyCondaPackageCache reload(@NotNull Multimap<String, String> nameToVersion) {
    ourInstance = new PyCondaPackageCache(nameToVersion);
    store(ourInstance, CACHE_FILE_NAME);
    return ourInstance;
  }

  private PyCondaPackageCache() {
  }

  private PyCondaPackageCache(@NotNull Multimap<String, String> nameToVersion) {
    for (String name : nameToVersion.keySet()) {
      myPackages.put(name, new PyAbstractPackageCache.PackageInfo(new ArrayList<>(nameToVersion.get(name))));
    }
  }
}
