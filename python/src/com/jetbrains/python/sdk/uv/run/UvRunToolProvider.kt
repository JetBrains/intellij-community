// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.eel.provider.localEel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.features.PyRunToolData
import com.jetbrains.python.run.features.PyRunToolId
import com.jetbrains.python.run.features.PyRunToolParameters
import com.jetbrains.python.run.features.PyRunToolProvider
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.uv.UvSdkAdditionalData
import com.jetbrains.python.sdk.uv.getUvExecutionContext
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.sdk.uv.isUv
import com.jetbrains.python.target.PyTargetAwareAdditionalData

/**
 * PyRunToolProvider implementation that runs scripts/modules using `uv run`.
 *
 * Matches both local UV SDKs ([UvSdkAdditionalData]) and remote ones
 * ([PyTargetAwareAdditionalData] wrapping `UvSdkFlavorData`).
 */
internal class UvRunToolProvider : PyRunToolProvider {

  override fun isAvailable(sdk: Sdk): Boolean = sdk.isUv

  override suspend fun getRunToolParameters(sdk: Sdk): PyRunToolParameters {
    val env = mutableMapOf<String, String>()
    val uvExecutionContext = sdk.getUvExecutionContext()
    val fileSystem = uvExecutionContext?.fileSystem ?: FileSystem.Eel(localEel)
    val uvExecutable = getUvExecutable(fileSystem, uvExecutionContext?.uvPath?.toString())?.toString()
    // TODO PY-87712 Duplicated code for setting up uv envs
    uvExecutionContext?.venvPath?.toString()?.let {
      env += "VIRTUAL_ENV" to it
      env += "UV_PROJECT_ENVIRONMENT" to it
    }
    return PyRunToolParameters(requireNotNull(uvExecutable) { "Unable to find uv executable." }, listOf("run"), env)
  }

  override val runToolData: PyRunToolData = PyRunToolData(
    PyRunToolId("uv.run"),
    PyBundle.message("uv.run.configuration.type.display.name"),
    PyBundle.message("python.run.configuration.fragments.python.group"),
  )

  override val initialToolState: Boolean = true
}
