// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author Mikhail Golubev
 */
public class CondaPackageCache {
  private static final Logger LOG = Logger.getInstance(PyPIPackageCache.class);
  private static final Gson ourGson = new GsonBuilder().create();
  private static CondaPackageCache ourInstance = null;

  @NotNull
  public static synchronized CondaPackageCache getInstance() {
    if (ourInstance == null) {
      ourInstance = new CondaPackageCache();
      try (Reader reader = Files.newBufferedReader(getCachePath(), StandardCharsets.UTF_8)) {
        ourInstance = ourGson.fromJson(reader, CondaPackageCache.class);
        LOG.info("Loaded " + ourInstance.myPackages.size() + " packages from " + getCachePath());
      }
      catch (IOException exception) {
        LOG.warn("Cannot load Conda package cache from the filesystem", exception);
      }
    }
    return ourInstance;
  }

  @NotNull
  public static synchronized CondaPackageCache reload(@NotNull Map<String, List<String>> packageNames) {
    ourInstance = new CondaPackageCache(packageNames);
    try {
      Files.createDirectories(getCachePath().getParent());
      try (Writer writer = Files.newBufferedWriter(getCachePath(), StandardCharsets.UTF_8)) {
        ourGson.toJson(ourInstance, writer);
      }
    }
    catch (IOException exception) {
      LOG.warn("Cannot save Conda package cache to the filesystem", exception);
    }
    return ourInstance;
  }

  @NotNull
  private static Path getCachePath() {
    return Paths.get(PathManager.getSystemPath(), "python_packages", "conda-cache.json");
  }

  public CondaPackageCache() {

  }

  private CondaPackageCache(@NotNull Map<String, List<String>> nameToVersion) {
    for (Map.Entry<String, List<String>> entry : nameToVersion.entrySet()) {
      myPackages.put(entry.getKey(), new PackageInfo(entry.getValue()));
    }
  }

  public boolean containsPackage(@NotNull String packageName) {
    return myPackages.containsKey(packageName);
  }

  @NotNull
  public Set<String> getPackageNames() {
    return Collections.unmodifiableSet(myPackages.keySet());
  }

  /**
   * Returns available package versions sorted in the reversed order using {@link com.intellij.webcore.packaging.PackageVersionComparator}.
   * @param packageName case-insensitive name of a package
   */
  @NotNull
  public List<String> getVersions(@NotNull String packageName) {
    return Collections.unmodifiableList(myPackages.get(packageName).getVersions());
  }

  @SerializedName("packages")
  private final TreeMap<String, PackageInfo> myPackages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  private static class PackageInfo {

    @SerializedName("v")
    private List<String> myVersions;

    public PackageInfo(@NotNull List<String> versions) {
      myVersions = versions;
    }

    @SuppressWarnings("unused")
    public PackageInfo() {
    }

    @NotNull
    public List<String> getVersions() {
      return Collections.unmodifiableList(myVersions);
    }
  }
}
