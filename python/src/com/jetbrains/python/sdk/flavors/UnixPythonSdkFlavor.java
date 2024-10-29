// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.jetbrains.python.sdk.VirtualEnvReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


public final class UnixPythonSdkFlavor extends CPythonSdkFlavor<PyFlavorData.Empty> {
  private static final String[] BIN_DIRECTORIES = new String[]{"/usr/bin", "/usr/local/bin"};
  // file names of system pythons
  private static final Set<@NonNls String> SYS_PYTHON_FILE_NAMES = Sets.newHashSet("pypy", "python3");

  private UnixPythonSdkFlavor() {
  }

  public static UnixPythonSdkFlavor getInstance() {
    return PythonSdkFlavor.EP_NAME.findExtension(UnixPythonSdkFlavor.class);
  }

  @Override
  public boolean isApplicable() {
    return SystemInfo.isUnix && !SystemInfo.isMac;
  }

  @Override
  public @NotNull Class<PyFlavorData.Empty> getFlavorDataClass() {
    return PyFlavorData.Empty.class;
  }

  @Override
  public @NotNull Collection<@NotNull Path> suggestLocalHomePaths(@Nullable Module module, @Nullable UserDataHolder context) {
    return getDefaultUnixPythons();
  }

  public static @NotNull List<Path> getDefaultUnixPythons() {
    return getDefaultUnixPythons(null);
  }

  public static @NotNull List<Path> getDefaultUnixPythons(@Nullable Path rootPath) {
    var candidates = new ArrayList<Path>();
    Arrays.stream(BIN_DIRECTORIES)
      .map(Path::of)
      .map(binDirectory -> optionallyChangeRoot(rootPath, binDirectory))
      .forEach(rootDir -> collectUnixPythons(rootDir, candidates));

    collectPyenvPythons(candidates);

    return candidates;
  }

  private static @NotNull Path optionallyChangeRoot(@Nullable Path rootPath, @NotNull Path dir) {
    return rootPath != null ? rootPath.resolve(dir.getRoot().relativize(dir)) : dir;
  }

  static void collectUnixPythons(@NotNull Path binDirectory, @NotNull Collection<Path> candidates) {
    try (var entries = Files.list(binDirectory)) {
      // Hack to exclude system python2
      entries.filter(path -> SYS_PYTHON_FILE_NAMES.contains(path.getFileName().toString()))
        .collect(Collectors.toCollection(() -> candidates));
    }
    catch (IOException ignored) {
    }
  }

  static void collectPyenvPythons(@NotNull Collection<Path> candidates) {
    candidates.addAll(VirtualEnvReader.getInstance().findPyenvInterpreters());
  }
}
