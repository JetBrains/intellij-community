// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.conda

import com.jetbrains.env.conda.LocalCondaRule.Companion.CONDA_PATH
import com.intellij.execution.target.FullPathOnTarget
import com.jetbrains.python.sdk.add.target.conda.suggestCondaPath
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import kotlinx.coroutines.runBlocking
import org.junit.AssumptionViolatedException
import org.junit.rules.ExternalResource
import java.nio.file.Path
import kotlin.io.path.isExecutable

/**
 * Finds conda on local system using [CONDA_PATH] env var.
 *
 * To be fixed: support targets as well
 */
class LocalCondaRule : ExternalResource() {
  private companion object {
    const val CONDA_PATH = "CONDA_PATH"
  }

  lateinit var condaPath: Path
    private set

  val condaPathOnTarget: FullPathOnTarget get() = condaPath.toString()
  val condaCommand: PyCondaCommand get() = PyCondaCommand(condaPathOnTarget, null)

  override fun before() {
    super.before()
    val condaPathEnv = System.getenv()[CONDA_PATH]
                       ?: runBlocking { suggestCondaPath(null) }
                       ?: throw AssumptionViolatedException("No $CONDA_PATH set")
    condaPath = Path.of(condaPathEnv)
    if (!condaPath.isExecutable()) {
      throw AssumptionViolatedException("$condaPath is not executable")
    }
  }
}