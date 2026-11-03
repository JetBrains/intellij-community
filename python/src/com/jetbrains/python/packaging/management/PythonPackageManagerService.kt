// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.serviceContainer.AlreadyDisposedException
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface PythonPackageManagerProvider {

  /**
   * Creates the [PythonPackageManager] for [interpreter], or `null` when this provider does not handle it.
   * The SDK of [interpreter] is a Python SDK with `PythonSdkAdditionalData`.
   */
  fun createPackageManager(project: Project, interpreter: PythonInterpreter): PythonPackageManager?

  companion object {
    val EP_NAME: ExtensionPointName<PythonPackageManagerProvider> =
      ExtensionPointName.create("Pythonid.pythonPackageManagerProvider")
  }
}

internal interface PythonPackageManagerService {
  @Throws(AlreadyDisposedException::class)
  fun forPythonInterpreter(project: Project, interpreter: PythonInterpreter): PythonPackageManager

  /** The cached manager of [interpreter], or `null` when no caller asked for it yet. It creates no manager. */
  fun cachedFor(interpreter: PythonInterpreter): PythonPackageManager?

  /**
   * Provides an implementation bridge for Python package management operations
   * specific to the given project and SDK. The bridge serves as a connection point
   * to enable advanced management tasks, potentially extending or adapting functionalities
   * provided by the [PythonPackageManager].
   */
  fun bridgeForSdk(project: Project, sdk: Sdk): PythonPackageManagementServiceBridge

}
