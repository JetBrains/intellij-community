// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyCustomPackageManagers")

package com.jetbrains.python.packaging

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus
@ApiStatus.Internal

@ApiStatus.Experimental
interface PyPackageManagerProvider {
  /**
   * Returns [PyPackageManager] if specified [sdk] is known to this provider
   * and `null` otherwise.
   */
  fun tryCreateForSdk(sdk: Sdk): PyPackageManager?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyPackageManagerProvider> = ExtensionPointName.create("Pythonid.packageManagerProvider")
  }
}

private val LOG: Logger = Logger.getInstance("#com.jetbrains.python.packaging.PyCustomPackageManagers")

/**
 * Returns the first [PyPackageManager] returned by available
 * [PyPackageManagerProvider]s for [sdk] specified. Returns `null` if all
 * [PyPackageManagerProvider]s returned `null` for this [sdk].
 */
@ApiStatus.Experimental
fun tryCreateCustomPackageManager(sdk: Sdk): PyPackageManager? {
  val managers: List<PyPackageManager> = PyPackageManagerProvider.EP_NAME.extensionList.mapNotNull { it.safeTryCreateForSdk(sdk) }
  if (managers.size > 1) {
    LOG.warn("Ambiguous Python package managers found: $managers")
  }
  return managers.firstOrNull()
}

/**
 * Fixes the problem when [PyPackageManagerProvider.tryCreateForSdk] for Docker
 * and Docker Compose types throws [NoClassDefFoundError] exception when
 * `org.jetbrains.plugins.remote-run` plugin is disabled.
 */
private fun PyPackageManagerProvider.safeTryCreateForSdk(sdk: Sdk): PyPackageManager? {
  try {
    return tryCreateForSdk(sdk)
  }
  catch (e: NoClassDefFoundError) {
    LOG.info(e)
    return null
  }
}