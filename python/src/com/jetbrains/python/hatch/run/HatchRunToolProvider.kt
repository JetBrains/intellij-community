// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.run

import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.impl.sdk.HatchSdkFlavor
import com.intellij.python.hatch.impl.sdk.HatchSdkFlavorData
import com.intellij.python.hatch.runtime.HatchConstants
import com.jetbrains.python.PyBundle
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.run.features.PyRunToolData
import com.jetbrains.python.run.features.PyRunToolId
import com.jetbrains.python.run.features.PyRunToolParameters
import com.jetbrains.python.run.features.PySdkRunToolProvider
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder

internal class HatchRunToolProvider : PySdkRunToolProvider<HatchSdkFlavorData, HatchSdkFlavor>(HatchSdkFlavor::class.java) {

  override suspend fun <P : PathHolder> getRunToolParameters(
    sdkHome: String,
    flavorData: HatchSdkFlavorData,
    fileSystem: FileSystem<P>,
  ): PyRunToolParameters {
    val hatchPath = HatchConfiguration.getOrDetectHatchExecutablePath(fileSystem).getOrThrow()
    val env = mutableMapOf<String, String>()
    flavorData.hatchEnvironmentName?.let {
      env += HatchConstants.AppEnvVars.ENV to it
    }
    return PyRunToolParameters(hatchPath.toString(), listOf("run", "python"), env, includeOriginalExe = false)
  }

  override val runToolData: PyRunToolData = PyRunToolData(
    PyRunToolId("hatch.run"),
    PyBundle.message("hatch.run.configuration.type.display.name"),
    PyBundle.message("python.run.configuration.fragments.python.group"),
  )

  override val initialToolState: Boolean = true
}
