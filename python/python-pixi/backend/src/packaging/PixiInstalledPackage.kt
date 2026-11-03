// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName

internal fun resolvePixiInstalledPackage(packages: List<PixiPackage>, name: String): PyResult<PixiPackage?> {
  val matches = packages.filter { PyPackageName.from(it.name).name == PyPackageName.from(name).name }
  if (matches.size > 1) {
    return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.dependency.ambiguous", name))
  }
  return PyResult.success(matches.singleOrNull())
}

internal fun resolvePixiNativePackageName(name: String, candidates: List<PixiNativePackage>): PyResult<String> {
  val names = candidates.map { it.name }.filter { PyPackageName.from(it).name == PyPackageName.from(name).name }.distinct()
  if (names.size > 1) {
    return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.dependency.ambiguous", name))
  }
  return PyResult.success(names.singleOrNull() ?: name)
}
