// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonPackageManagementListener {
  fun packagesChanged(sdk: Sdk) {}

  @ApiStatus.Internal
  fun outdatedPackagesChanged(sdk: Sdk) {
  }
}