// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hatch.run

import com.intellij.python.hatch.HatchConfiguration
import com.intellij.python.hatch.runtime.HatchConstants
import com.jetbrains.python.PyBundle
import com.jetbrains.python.hatch.sdk.HatchSdkAdditionalData
import com.jetbrains.python.run.features.PyRunToolData
import com.jetbrains.python.run.features.PyRunToolId
import com.jetbrains.python.run.features.PyRunToolParameters
import com.jetbrains.python.run.features.PySdkRunToolProvider

internal class HatchRunToolProvider : PySdkRunToolProvider<HatchSdkAdditionalData>(HatchSdkAdditionalData::class.java) {

  override suspend fun getRunToolParameters(data: HatchSdkAdditionalData): PyRunToolParameters {
    val hatchPath = HatchConfiguration.getOrDetectHatchExecutablePath().getOr { error("Unable to find hatch executable.") }
    val env = mutableMapOf<String, String>()
    data.hatchEnvironmentName?.let {
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
