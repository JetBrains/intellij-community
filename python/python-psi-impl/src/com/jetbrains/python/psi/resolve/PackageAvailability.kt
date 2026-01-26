// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import org.jetbrains.annotations.ApiStatus

/**
 * Specification for checking package availability in a Python environment.
 *
 * Uses a combined strategy:
 * 1. First checks the package manager cache (fast, non-blocking)
 * 2. Falls back to PSI resolution on disk (cached)
 *
 * Create instances as constants and pass them to [isPackageAvailable]:
 * ```kotlin
 * private val PYTEST_SPEC = PackageAvailabilitySpec("pytest", "pytest.fixture")
 *
 * fun isPytestInstalled(module: Module) = module.isPackageAvailable(PYTEST_SPEC)
 * ```
 *
 * Multiple FQNs can be provided for packages that expose symbols at different paths across versions:
 * ```kotlin
 * private val BLACK_SPEC = PackageAvailabilitySpec("black", "black.Mode", "black.mode.Mode")
 * ```
 *
 * @param packageName the package name as it appears in pip/package manager (e.g., "pytest", "Django", "djangorestframework")
 * @param fqns the fully-qualified names to resolve for PSI fallback (e.g., "pytest.fixture", "django.conf.settings").
 *             Resolution succeeds if any of the FQNs resolves.
 */
@ApiStatus.Internal
class PackageAvailabilitySpec(
  val packageName: String,
  vararg fqns: String,
) {
  val fqns: List<String> = fqns.toList()

  @ApiStatus.Internal
  val cacheKey: Key<CachedValue<Boolean>> = Key.create("isPackageAvailable:$packageName:${fqns.joinToString(",")}")
}

/**
 * Checks if a package is available in the module's Python environment.
 *
 * Uses a combined strategy:
 * 1. First checks package manager cache (fast)
 * 2. Falls back to PSI resolution if not found in package manager
 *
 * @param spec the package availability specification
 * @return true if the package is available, false otherwise
 */
@ApiStatus.Internal
fun isPackageAvailable(module: Module, spec: PackageAvailabilitySpec): Boolean {
  return PackageAvailabilityService.getInstance().isPackageAvailable(module, spec)
}

/**
 * Checks if a package is available in the SDK's Python environment.
 *
 * Uses a combined strategy:
 * 1. First checks package manager cache (fast)
 * 2. Falls back to PSI resolution if not found in package manager
 *
 * @param spec the package availability specification
 * @return true if the package is available, false otherwise
 */
@ApiStatus.Internal
fun isPackageAvailable(project: Project, sdk: Sdk, spec: PackageAvailabilitySpec): Boolean {
  return PackageAvailabilityService.getInstance().isPackageAvailable(project, sdk, spec)
}

/**
 * Service for checking package availability in Python environments.
 *
 * Uses a combined strategy:
 * 1. First checks package manager cache (fast, non-blocking)
 * 2. Falls back to PSI resolution on disk (cached)
 *
 * This service is implemented in a higher-level module (python.community.impl) that has access
 * to [com.jetbrains.python.packaging.management.PythonPackageManager].
 */
@ApiStatus.Internal
interface PackageAvailabilityService {
  /**
   * Checks if a package is available in the module's Python environment.
   */
  fun isPackageAvailable(module: Module, spec: PackageAvailabilitySpec): Boolean

  /**
   * Checks if a package is available in the SDK's Python environment.
   */
  fun isPackageAvailable(project: Project, sdk: Sdk, spec: PackageAvailabilitySpec): Boolean

  companion object {
    @JvmStatic
    fun getInstance(): PackageAvailabilityService = ApplicationManager.getApplication().getService(PackageAvailabilityService::class.java)
  }
}
