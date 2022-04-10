// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib;

import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author vlan
 */
public final class PyStdlibUtil {
  @Nullable private static final Set<String> PACKAGES = loadStdlibPackagesList();

  private PyStdlibUtil() {
  }

  @Nullable
  public static Collection<String> getPackages() {
    return PACKAGES;
  }

  @Nullable
  private static Set<String> loadStdlibPackagesList() {
    final Logger log = Logger.getInstance(PyStdlibUtil.class.getName());
    final String helperPath = PythonHelpersLocator.getHelperPath("/tools/stdlib_packages.txt");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(helperPath), StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.toSet());
    }
    catch (IOException e) {
      log.error("Cannot read list of standard library packages: " + e.getMessage());
    }
    return null;
  }
}
