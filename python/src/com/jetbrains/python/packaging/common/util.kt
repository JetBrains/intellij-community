// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import org.jetbrains.annotations.ApiStatus

/**
 * Hears package changes in an environment. The package manager calls the forms that take a [PythonInterpreter].
 * By default they forward to the older forms that take an [Sdk], so a listener that overrides only those keeps working.
 */
@ApiStatus.Experimental
interface PythonPackageManagementListener {
  /** The installed packages of [interpreter] changed. */
  fun packagesChanged(interpreter: PythonInterpreter) {
    @Suppress("DEPRECATION") // The forward to the older form, see there.
    packagesChanged(interpreter.getSdkAPI())
  }

  /** The outdated packages of [interpreter] changed. */
  @ApiStatus.Internal
  fun outdatedPackagesChanged(interpreter: PythonInterpreter) {
    @Suppress("DEPRECATION") // The forward to the older form, see there.
    outdatedPackagesChanged(interpreter.getSdkAPI())
  }

  /** The older form of [packagesChanged]. A new listener overrides the form that takes a [PythonInterpreter]. */
  fun packagesChanged(sdk: Sdk) {}

  /** The older form of [outdatedPackagesChanged]. A new listener overrides the form that takes a [PythonInterpreter]. */
  @ApiStatus.Internal
  fun outdatedPackagesChanged(sdk: Sdk) {
  }
}