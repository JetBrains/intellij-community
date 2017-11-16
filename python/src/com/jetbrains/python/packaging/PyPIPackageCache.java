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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyPIPackageCache {
  private static final Logger LOG = Logger.getInstance(PyPIPackageCache.class);
  private static final Gson ourGson = new GsonBuilder().create();
  private static PyPIPackageCache ourInstance = null;

  @NotNull
  public static synchronized PyPIPackageCache getInstance() {
    if (ourInstance == null) {
      ourInstance = new PyPIPackageCache();
      try (Reader reader = Files.newBufferedReader(getCachePath(), StandardCharsets.UTF_8)) {
        ourInstance = ourGson.fromJson(reader, PyPIPackageCache.class);
        LOG.info("Loaded " + ourInstance.getPackageNames().size() + " packages from " + getCachePath());
      }
      catch (NoSuchFileException ignored) {
      }
      catch (IOException exception) {
        LOG.warn("Cannot load PyPI package cache from the filesystem", exception);
      }
    }
    return ourInstance;
  }

  @NotNull
  public static synchronized PyPIPackageCache reload(@NotNull List<String> packageNames) {
    ourInstance = new PyPIPackageCache(packageNames);
    try {
      Files.createDirectories(getCachePath().getParent());
      try (Writer writer = Files.newBufferedWriter(getCachePath(), StandardCharsets.UTF_8)) {
        ourGson.toJson(ourInstance, writer);
      }
    }
    catch (IOException exception) {
      LOG.warn("Cannot save PyPI package cache to the filesystem", exception);
    }
    return ourInstance;
  }

  // For GSON
  private PyPIPackageCache() {
  }

  public PyPIPackageCache(@NotNull List<String> packageNames) {
    myPackageNames = packageNames;
  }

  @NotNull
  private static Path getCachePath() {
    return Paths.get(PathManager.getSystemPath(), "python_packages", "pypi-cache.json");
  }

  @SerializedName("packages")
  private List<String> myPackageNames = new ArrayList<>();

  @NotNull
  public List<String> getPackageNames() {
    return Collections.unmodifiableList(myPackageNames);
  }

  /**
   * Checks that the given name is among those available at PyPI <em>case-insensitively</em>.
   * <p>
   * Note that if the cache hasn't been initialized yet or there was an error during its loading,
   * {@link #getInstance()} returns an empty sentinel value, and, therefore, this method will return {@code false}.
   * It's worth writing code analysis so that this value doesn't lead to false positives in the editor
   * when the cache is merely not ready.
   */
  public boolean containsPackage(@NotNull String name) {
    return Collections.binarySearch(myPackageNames, name, String.CASE_INSENSITIVE_ORDER) >= 0;
  }
}
