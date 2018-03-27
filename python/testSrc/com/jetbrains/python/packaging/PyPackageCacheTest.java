/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.packaging;

import com.jetbrains.python.fixtures.PyTestCase;

import java.nio.file.Paths;

/**
 * @author Mikhail Golubev
 */
public class PyPackageCacheTest extends PyTestCase {

  // PY-28016
  public void testCaseInsensitivePackageNameMatching() {
    final PyPIPackageCache cache = PyPIPackageCache.getInstance(Paths.get(getTestDataPath(), "pypi-cache.json"));

    assertTrue(cache.containsPackage("flask"));
    assertTrue(cache.containsPackage("Flask"));
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/packaging";
  }
}
