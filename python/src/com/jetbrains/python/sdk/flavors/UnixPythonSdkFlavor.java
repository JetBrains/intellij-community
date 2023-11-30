// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public final class UnixPythonSdkFlavor extends CPythonSdkFlavor<PyFlavorData.Empty> {
  private static final String[] BIN_DIRECTORIES = new String[]{"/usr/bin", "/usr/local/bin"};
  private static final String[] NAMES = new String[]{"jython", "pypy"};
  private static final Pattern PYTHON_3_RE = Pattern.compile("(python-?3\\.(\\d){1,2})|(python-?3)");

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
    return candidates;
  }

  private static @NotNull Path optionallyChangeRoot(@Nullable Path rootPath, @NotNull Path dir) {
    return rootPath != null ? rootPath.resolve(dir.getRoot().relativize(dir)) : dir;
  }

  public static void collectUnixPythons(@NotNull Path binDirectory, @NotNull Collection<Path> candidates) {
    try (var entries = Files.list(binDirectory)) {
      entries.filter(UnixPythonSdkFlavor::looksLikePythonBinary).collect(Collectors.toCollection(() -> candidates));
    }
    catch (IOException ignored) {
    }
  }

  private static boolean looksLikePythonBinary(@NotNull Path path) {
    if (!Files.isRegularFile(path)) return false;
    return looksLikePythonBinaryFilename(path.getFileName().toString());
  }

  private static boolean looksLikePythonBinaryFilename(@NotNull String filename) {
    String childName = StringUtil.toLowerCase(filename);
    return ArrayUtil.contains(childName, NAMES) || PYTHON_3_RE.matcher(childName).matches();
  }
}
