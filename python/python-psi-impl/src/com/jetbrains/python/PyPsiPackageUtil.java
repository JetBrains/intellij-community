package com.jetbrains.python;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ResourceUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.packaging.PyPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Python package utility methods with no dependencies on the Python runtime.
 *
 * @see PyPackageUtil for other package utility methods, including run-time dependent parts.
 */
public class PyPsiPackageUtil {
  private static final Logger LOG = Logger.getInstance(PyPsiPackageUtil.class);

  /**
   * Contains mapping "importable top-level package" -> "package names on PyPI".
   */
  public static final ImmutableMap<String, List<String>> PACKAGES_TOPLEVEL = loadPackageAliases();

  @Nullable
  public static PyPackage findPackage(@NotNull List<? extends PyPackage> packages, @NotNull String name) {
    for (PyPackage pkg : packages) {
      if (name.equalsIgnoreCase(pkg.getName())) {
        return pkg;
      }
    }
    return null;
  }

  @NotNull
  private static ImmutableMap<String, List<String>> loadPackageAliases() {
    final ImmutableMap.Builder<String, List<String>> builder = ImmutableMap.builder();
    try {
      Arrays.stream(StringUtil.splitByLines(ResourceUtil.loadText(PyPsiPackageUtil.class.getResource("/tools/packages"))))
        .forEach(
          line -> {
            final List<String> split = StringUtil.split(line, " ");
            builder.put(split.get(0), new SmartList<>(ContainerUtil.subList(split, 1)));
          }
        );
    }
    catch (IOException e) {
      LOG.error("Cannot find \"packages\". " + e.getMessage());
    }
    return builder.build();
  }
}
