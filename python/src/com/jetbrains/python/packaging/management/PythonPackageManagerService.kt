// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import kotlinx.coroutines.CoroutineScope
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

@ApiStatus.Internal
@ApiStatus.Experimental
interface PythonPackageManagerService {
  fun forSdk(project: Project, sdk: Sdk): PythonPackageManager

  /**
   * Provides an implementation bridge for Python package management operations
   * specific to the given project and SDK. The bridge serves as a connection point
   * to enable advanced management tasks, potentially extending or adapting functionalities
   * provided by the [PythonPackageManager].
   */
  fun bridgeForSdk(project: Project, sdk: Sdk): PythonPackageManagementServiceBridge

  fun getServiceScope(): CoroutineScope
}