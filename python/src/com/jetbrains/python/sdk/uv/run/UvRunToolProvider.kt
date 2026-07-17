// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.jetbrains.python.PyBundle
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.run.features.PyRunToolData
import com.jetbrains.python.run.features.PyRunToolId
import com.jetbrains.python.run.features.PyRunToolParameters
import com.jetbrains.python.run.features.PySdkRunToolProvider
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.uv.UvSdkAdditionalData
import com.jetbrains.python.sdk.uv.UvSdkFlavor
import com.jetbrains.python.sdk.uv.UvSdkFlavorData
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.target.PyTargetAwareAdditionalData

/**
 * PyRunToolProvider implementation that runs scripts/modules using `uv run`.
 *
 * Matches both local UV SDKs ([UvSdkAdditionalData]) and remote ones
 * ([PyTargetAwareAdditionalData] wrapping `UvSdkFlavorData`).
 */
internal class UvRunToolProvider : PySdkRunToolProvider<UvSdkFlavorData, UvSdkFlavor>(UvSdkFlavor::class.java) {

  override suspend fun <P : PathHolder> getRunToolParameters(
    sdkHome: String,
    flavorData: UvSdkFlavorData,
    fileSystem: FileSystem<P>,
  ): PyRunToolParameters {
    val env = mutableMapOf<String, String>()
    val uvExecutable = getUvExecutable(fileSystem, flavorData.uvPath)?.toString()
    // TODO PY-87712 Duplicated code for setting up uv envs
    val pythonPath = fileSystem.parsePath(sdkHome).getOrThrow()
    val venvPath = fileSystem.resolvePythonHome(pythonPath).toString()
    env += "VIRTUAL_ENV" to venvPath
    env += "UV_PROJECT_ENVIRONMENT" to venvPath
    return PyRunToolParameters(requireNotNull(uvExecutable) { "Unable to find uv executable." }, listOf("run"), env)
  }

  override val runToolData: PyRunToolData = PyRunToolData(
    PyRunToolId("uv.run"),
    PyBundle.message("uv.run.configuration.type.display.name"),
    PyBundle.message("python.run.configuration.fragments.python.group"),
  )

  override val initialToolState: Boolean = true
}
