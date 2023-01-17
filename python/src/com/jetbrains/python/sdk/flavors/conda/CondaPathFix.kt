// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors.conda

import com.intellij.execution.Platform
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.EnvReader
import com.intellij.util.io.exists
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.flavors.conda.CondaPathFix.ByCondaFullPath
import com.jetbrains.python.sdk.flavors.conda.CondaPathFix.BySdk
import com.jetbrains.python.sdk.flavors.conda.CondaPathFix.Companion.shouldBeFixed
import java.nio.file.Path
import kotlin.io.path.isExecutable

/**
 * Workaround for cases like ``https://github.com/conda/conda/issues/11795``
 *
 * It reads envs vars out of "activate.bat" on Windows.
 * Something unreliable and redundant, but still needed due to conda bugs.
 *
 * First, check with [shouldBeFixed], then choose if you have SDK  ([BySdk]) or bare local conda path ([ByCondaFullPath]).
 * Call [fix]
 */
sealed class CondaPathFix {
  companion object {
    /**
     * Should this fix be applied at all?
     * Fix only required for local Windows
     */
    val TargetedCommandLineBuilder.shouldBeFixed: Boolean
      get() = request.let {
        it.configuration == null && it.targetPlatform.platform == Platform.WINDOWS
      }
  }

  class BySdk(private val sdk: Sdk) : CondaPathFix() {
    override val vars: Map<String, String>
      get() {
        val pythonHomePath = sdk.homePath
        if (pythonHomePath == null) {
          Logger.getInstance(BySdk::class.java).warn("No home path for $this, will skip 'venv activation'")
          return emptyMap()
        }
        return PySdkUtil.activateVirtualEnv(sdk)
      }
  }

  class ByCondaFullPath(private val condaPath: Path) : CondaPathFix() {
    override val vars: Map<String, String>
      get() {
        if (!condaPath.exists()) {
          Logger.getInstance(ByCondaFullPath::class.java).warn("$condaPath doesn't exist")
          return emptyMap()
        }
        val activateBat = condaPath.resolveSibling("activate.bat")
        if (!activateBat.isExecutable()) {
          Logger.getInstance(ByCondaFullPath::class.java).warn("$activateBat doesn't exist or can't be read")
          return emptyMap()
        }
        return EnvReader().readBatEnv(activateBat, emptyList())
      }
  }

  /**
   * Call if [shouldBeFixed] only
   */
  fun fix(commandLineBuilder: TargetedCommandLineBuilder) {
    assert(commandLineBuilder.shouldBeFixed) { "Check with shouldBeFixed" }
    vars.forEach { commandLineBuilder.addEnvironmentVariable(it.key, it.value) }
  }

  protected abstract val vars: Map<String, String>
}