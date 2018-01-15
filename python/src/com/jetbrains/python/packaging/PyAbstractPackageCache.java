// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author Mikhail Golubev
 */
public abstract class PyAbstractPackageCache {
  private static final Logger LOG = Logger.getInstance(PyPIPackageCache.class);

  private static final Gson ourGson = new GsonBuilder()
      // Otherwise, GSON uses natural order comparator even for a final TreeMap field
    .registerTypeAdapter(new TypeToken<TreeMap<String, PackageInfo>>() { }.getType(),
                         (InstanceCreator)type -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
    .create();

  @SerializedName("packages")
  protected final TreeMap<String, PackageInfo> myPackages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  protected PyAbstractPackageCache() {
  }


  @NotNull
  protected static <T extends PyAbstractPackageCache> T load(@NotNull Class<T> classToken,
                                                             @NotNull T fallbackValue,
                                                             @NotNull Path cacheFilePath) {
    T cache = fallbackValue;
    try (Reader reader = Files.newBufferedReader(cacheFilePath, StandardCharsets.UTF_8)) {
      cache = ourGson.fromJson(reader, classToken);
      LOG.info("Loaded " + cache.getPackageNames().size() + " packages from " + cacheFilePath);
    }
    catch (IOException exception) {
      LOG.warn("Cannot load " + cacheFilePath + " package cache from the filesystem", exception);
    }
    return cache;
  }

  protected static void store(@NotNull PyAbstractPackageCache newValue, @NotNull String cacheFileName) {
    try {
      final Path cacheFilePath = getDefaultCachePath(cacheFileName);
      Files.createDirectories(cacheFilePath.getParent());
      try (Writer writer = Files.newBufferedWriter(cacheFilePath, StandardCharsets.UTF_8)) {
        ourGson.toJson(newValue, writer);
      }
    }
    catch (IOException exception) {
      LOG.warn("Cannot save " + cacheFileName + " package cache to the filesystem", exception);
    }
  }

  @NotNull
  protected static Path getDefaultCachePath(@NotNull String cacheFileName) {
    return Paths.get(PathManager.getSystemPath(), "python_packages", cacheFileName);
  }

  /**
   * Returns a case-insensitive set of packages names available in the cache.
   */
  @NotNull
  public Set<String> getPackageNames() {
    return Collections.unmodifiableSet(myPackages.keySet());
  }

  /**
   * Checks that the given name is among those available in the repository <em>case-insensitively</em>.
   * <p>
   * Note that if the cache hasn't been initialized yet or there was an error during its loading,
   * {@link #load(Class, PyAbstractPackageCache, Path)} returns an empty sentinel value, and, therefore, this method will return {@code false}.
   * It's worth writing code analysis so that this value doesn't lead to false positives in the editor
   * when the cache is merely not ready.
   *
   * @param name case-insensitive name of a package
   */
  public boolean containsPackage(@NotNull String name) {
    return myPackages.containsKey(name);
  }

  /**
   * Returns available package versions sorted in the reversed order using
   * {@link com.intellij.webcore.packaging.PackageVersionComparator} so that the latest version is the first on the list
   * or {@code null} if the given package is not contained in the cache or this feature is not available.
   *
   * @param packageName case-insensitive name of a package
   */
  @Nullable
  public List<String> getVersions(@NotNull String packageName) {
    final PackageInfo packageInfo = myPackages.get(packageName);
    return packageInfo != null ? packageInfo.getVersions() : null;
  }

  protected static class PackageInfo {
    public static final PackageInfo EMPTY = new PackageInfo();

    @SerializedName("v")
    private List<String> myVersions;

    public PackageInfo(@NotNull List<String> versions) {
      myVersions = versions;
    }

    @SuppressWarnings("unused")
    public PackageInfo() {
    }

    @Nullable
    public List<String> getVersions() {
      return myVersions != null ? Collections.unmodifiableList(myVersions) : null;
    }
  }
}
