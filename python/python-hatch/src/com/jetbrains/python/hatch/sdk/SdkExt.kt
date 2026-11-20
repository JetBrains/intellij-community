// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.hatch.HatchService
import com.intellij.python.hatch.HatchPyTool
import com.intellij.python.hatch.getHatchService
import com.intellij.python.hatch.impl.sdk.HatchSdkFlavorData
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.toFileSystem
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pySdkAdditionalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Internal
val Sdk.isHatch: Boolean
  get() = hatchFlavorData != null

/** Whether Hatch manages the environment of this interpreter, see [com.intellij.python.sdk.common.PyInterpreterRef.manager]. */
internal val PythonInterpreter.isHatch: Boolean
  get() = ref.manager == HatchPyTool.getInstance().fusId

private val Sdk.hatchFlavorData: HatchSdkFlavorData?
  get() {
    if (!PythonSdkUtil.isPythonSdk(this)) {
      return null
    }

    return pySdkAdditionalData.flavorAndData.data as? HatchSdkFlavorData
  }

/**
 * The Hatch service of this interpreter, started lazily in [scope]. Hatch runs in the directory of the PyProject that
 * owns the interpreter, for the Hatch environment that its ref names.
 */
internal fun PythonInterpreter.createHatchServiceAsync(scope: CoroutineScope): Deferred<PyResult<HatchService<*>>> =
  scope.async(start = CoroutineStart.LAZY) {
    ownerDir.getHatchService(
      fileSystem = toFileSystem(),
      hatchEnvironmentName = ref.envRef.value,
    )
  }
