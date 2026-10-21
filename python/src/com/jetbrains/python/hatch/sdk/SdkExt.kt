// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.hatch.HatchService
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.impl.sdk.HatchSdkFlavorData
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pySdkAdditionalData
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

internal val Sdk.isHatch: Boolean
  get() = hatchFlavorData != null

/** Whether this interpreter is a Hatch environment. */
internal val PythonInterpreter.isHatch: Boolean
  @Suppress("DEPRECATION") // The Hatch flavor data lives in the SDK additional data.
  get() = getSdkAPI().isHatch

private val Sdk.hatchFlavorData: HatchSdkFlavorData?
  get() {
    if (!PythonSdkUtil.isPythonSdk(this)) {
      return null
    }

    return pySdkAdditionalData.flavorAndData.data as? HatchSdkFlavorData
  }

/** The Hatch service of this interpreter, started lazily in [scope], or `null` when it is no Hatch environment. */
internal fun PythonInterpreter.createHatchServiceAsync(scope: CoroutineScope): Deferred<PyResult<HatchService<*>>>? {
  // The flavor data and the working directory live in the SDK additional data.
  @Suppress("DEPRECATION")
  val sdk = getSdkAPI()
  val data = sdk.pySdkAdditionalData
  val flavorData = sdk.hatchFlavorData ?: return null
  val workingDirectory = data.workingDirectory.takeIf { data.hasValidWorkingDirectory() } ?: return null

  return when (data) {
    is HatchSdkAdditionalData, is PyTargetAwareAdditionalData -> {
      scope.async(start = CoroutineStart.LAZY) {
        workingDirectory.getHatchService(
          fileSystem = sdk.toFileSystem(),
          hatchEnvironmentName = flavorData.hatchEnvironmentName,
        )
      }
    }
    else -> null
  }
}
