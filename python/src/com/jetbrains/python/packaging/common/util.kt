// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import org.jetbrains.annotations.ApiStatus

/** Package changes of an environment. The interpreter forms forward to the [Sdk] forms by default. */
@ApiStatus.Experimental
interface PythonPackageManagementListener {
  /** The installed packages of [interpreter] changed. */
  @ApiStatus.Internal
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

  /** The old form. Override the [PythonInterpreter] form instead. */
  fun packagesChanged(sdk: Sdk) {}

  /** The old form. Override the [PythonInterpreter] form instead. */
  @ApiStatus.Internal
  fun outdatedPackagesChanged(sdk: Sdk) {
  }
}