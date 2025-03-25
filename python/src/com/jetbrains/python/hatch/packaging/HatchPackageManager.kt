// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.packaging

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.hatch.sdk.HatchSdkAdditionalData
import com.jetbrains.python.hatch.sdk.isHatch
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.pip.PipPythonPackageManager

class HatchPackageManager(project: Project, sdk: Sdk) : PipPythonPackageManager(project, sdk) {
  fun getSdkAdditionalData(): Result<HatchSdkAdditionalData, PyError> {
    val data = sdk.sdkAdditionalData as? HatchSdkAdditionalData
               ?: return Result.failure(PyError.Message("SDK is outdated, please recreate it"))
    return Result.success(data)
  }
}

class HatchPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? = when {
    sdk.isHatch -> HatchPackageManager(project, sdk)
    else -> null
  }
}

