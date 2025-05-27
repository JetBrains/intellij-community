// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PythonPackageManagerExt")

package com.jetbrains.python.packaging.management

import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
fun PythonPackageManager.hasInstalledPackage(packageName: String, version: String? = null): Boolean =
  getPackage(packageName, version) != null

@ApiStatus.Internal
fun PythonPackageManager.getPackage(packageName: String, version: String? = null): PythonPackage? {
  return installedPackages.firstOrNull { it.name == packageName && (version == null || version == it.version) }
}

@ApiStatus.Internal
fun PythonRepositoryManager.packagesByRepository(): Sequence<Pair<PyPackageRepository, Set<String>>> {
  return repositories.asSequence().map { it to it.getPackages() }
}

@ApiStatus.Internal
fun PythonPackageManager.isInstalled(name: String): Boolean {
  return installedPackages.any { it.name.equals(name, ignoreCase = true) }
}