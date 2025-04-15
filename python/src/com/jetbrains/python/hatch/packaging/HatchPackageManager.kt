// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.packaging

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.hatch.sdk.HatchSdkAdditionalData
import com.jetbrains.python.hatch.sdk.isHatch
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.pip.PipPythonPackageManager

internal class HatchPackageManager(project: Project, sdk: Sdk) : PipPythonPackageManager(project, sdk) {
  fun getSdkAdditionalData(): HatchSdkAdditionalData {
    return sdk.sdkAdditionalData as? HatchSdkAdditionalData
           ?: error("SDK [${sdk.name}] has illegal state, " +
                    "additional data has to be ${HatchSdkAdditionalData::class.java.name}, " +
                    "but was ${sdk.sdkAdditionalData?.javaClass?.name}")
  }
}

internal class HatchPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? = when {
    sdk.isHatch -> HatchPackageManager(project, sdk)
    else -> null
  }
}

