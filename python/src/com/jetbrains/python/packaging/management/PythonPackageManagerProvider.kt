// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonPackageManagerProvider {

  /**
   * Creates PythonPackageManager for Python SDK depending on interpreter type,
   * package management files etc.
   * Sdk is expected to be a Python Sdk and have PythonSdkAdditionalData.
   */
  fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager?

  companion object {
    val EP_NAME = ExtensionPointName.create<PythonPackageManagerProvider>("Pythonid.pythonPackageManagerProvider")
  }
}