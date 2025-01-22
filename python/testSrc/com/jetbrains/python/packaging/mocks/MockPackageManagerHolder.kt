// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.mocks

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.packaging.common.PackageManagerHolder
import com.jetbrains.python.packaging.management.PythonPackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.TestOnly


@TestOnly
class MockPackageManagerHolder() : PackageManagerHolder {

  override fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
    return MockPythonPackageManager(project, sdk)
  }

  override fun bridgeForSdk(project: Project, sdk: Sdk): PythonPackageManagementServiceBridge {
    return PythonPackageManagementServiceBridge(project, sdk)
  }

  override fun getServiceScope(): CoroutineScope {
    return CoroutineScope(Job())
  }
}