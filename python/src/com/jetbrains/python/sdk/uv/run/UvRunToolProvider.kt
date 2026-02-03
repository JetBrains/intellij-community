// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.features.PyRunToolData
import com.jetbrains.python.run.features.PyRunToolId
import com.jetbrains.python.run.features.PyRunToolParameters
import com.jetbrains.python.run.features.PyRunToolProvider
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import com.jetbrains.python.sdk.uv.isUv
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * PyRunToolProvider implementation that runs scripts/modules using `uv run`.
 */
internal class UvRunToolProvider : PyRunToolProvider {

  override suspend fun getRunToolParameters(): PyRunToolParameters {
    if (!runToolParameters.isCompleted) {
      runToolParametersMutex.withLock {
        if (!runToolParameters.isCompleted) {
          runToolParameters.complete(
            PyRunToolParameters(requireNotNull(getUvExecutable()?.toString()) { "Unable to find uv executable." }, listOf("run"))
          )
        }
      }
    }

    return runToolParameters.await()
  }

  override val runToolData: PyRunToolData = PyRunToolData(
    PyRunToolId("uv.run"),
    PyBundle.message("uv.run.configuration.type.display.name"),
    PyBundle.message("python.run.configuration.fragments.python.group"),
  )

  override val initialToolState: Boolean = true

  override fun isAvailable(sdk: Sdk): Boolean = sdk.isUv

  /**
   * We use runToolParameters only if a tool provider is available. So we need to have a lazy initialization here
   * to construct these parameters iff the validation has passed.
   */
  private val runToolParameters = CompletableDeferred<PyRunToolParameters>()
  private val runToolParametersMutex = Mutex()
}