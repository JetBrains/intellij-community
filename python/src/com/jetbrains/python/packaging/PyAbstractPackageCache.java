// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal

public abstract class PyAbstractPackageCache {
  private static final Logger LOG = Logger.getInstance(PyPIPackageCache.class);

  private static final Gson ourGson = new GsonBuilder()
          // Otherwise, GSON uses natural order comparator even for a final TreeMap field
          .registerTypeAdapter(new TypeToken<TreeMap<String, PackageInfo>>() { }.getType(),
                  (InstanceCreator<TreeMap<String, PackageInfo>>) type -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
          .registerTypeAdapter(PackageInfo.class,
                  new JsonDeserializer<PackageInfo>() {
                    final Gson defaultGson = new Gson();

                    @Override
                    public PackageInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                            throws JsonParseException {
                      if (json.isJsonObject() && json.getAsJsonObject().isEmpty()) {
                        return PackageInfo.EMPTY;
                      }
                      return defaultGson.fromJson(json, typeOfT);
                    }
                  })
          .create();

  @SerializedName("packages")
  protected final TreeMap<String, PackageInfo> myPackages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  protected PyAbstractPackageCache() {
  }


  protected static @NotNull <T extends PyAbstractPackageCache> T load(@NotNull Class<T> classToken,
                                                                      @NotNull T fallbackValue,
                                                                      @NotNull Path cacheFilePath) {
    T cache = fallbackValue;
    try (Reader reader = Files.newBufferedReader(cacheFilePath, StandardCharsets.UTF_8)) {
      cache = ourGson.fromJson(reader, classToken);
      LOG.info("Loaded " + cache.getPackageNames().size() + " packages from " + cacheFilePath);
    }
    catch (NoSuchFileException exception) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.info("Package cache " + cacheFilePath + " was not found");
      }
    }
    catch (JsonSyntaxException exception) {
      LOG.warn("Corrupted package cache " + cacheFilePath, exception);
      try {
        // It will be rebuilt on the next startup or displaying packaging UI
        Files.deleteIfExists(cacheFilePath);
      }
      catch (IOException ignored) {
      }
    }
    catch (IOException | JsonIOException exception) {
      LOG.warn("Cannot load package cache " + cacheFilePath, exception);
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
    catch (IOException | JsonIOException exception) {
      LOG.error("Cannot save " + cacheFileName + " package cache to the filesystem", exception);
    }
  }

  protected static @NotNull Path getDefaultCachePath(@NotNull String cacheFileName) {
    return Paths.get(PathManager.getSystemPath(), "python_packages", cacheFileName);
  }

  /**
   * Returns a case-insensitive set of packages names available in the cache.
   */
  public @NotNull Set<String> getPackageNames() {
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

  @Override
  public String toString() {
    return String.format("%s(size=%d): %s...", getClass().getSimpleName(), myPackages.size(),
                         StreamEx.ofKeys(myPackages).limit(5).joining(", "));
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

    public @Nullable List<String> getVersions() {
      return myVersions != null ? Collections.unmodifiableList(myVersions) : null;
    }
  }
}
