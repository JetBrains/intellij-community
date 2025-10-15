// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv.run

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.features.PyRunToolData
import com.jetbrains.python.run.features.PyRunToolId
import com.jetbrains.python.run.features.PyRunToolParameters
import com.jetbrains.python.run.features.PyRunToolProvider
import com.jetbrains.python.sdk.uv.isUv

/**
 * PyRunToolProvider implementation that runs scripts/modules using `uv run`.
 */
private class UvRunToolProvider : PyRunToolProvider {

  override val runToolData: PyRunToolData = PyRunToolData(
    PyRunToolId("py.run.with.uv"),
    PyBundle.message("uv.run.configuration.type.display.name"),
    PyBundle.message("python.run.configuration.fragments.python.group"),
  )

  override val runToolParameters: PyRunToolParameters = PyRunToolParameters(
    "uv",
    listOf("run")
  )

  override val initialToolState: Boolean = true

  override fun isAvailable(sdk: Sdk): Boolean = sdk.isUv
}