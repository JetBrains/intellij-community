// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.conda

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.jetbrains.python.tools.PythonType
import com.jetbrains.python.sdk.conda.TargetCommandExecutor
import com.jetbrains.python.sdk.conda.TargetEnvironmentRequestCommandExecutor
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import kotlinx.coroutines.runBlocking
import org.junit.AssumptionViolatedException
import org.junit.rules.ExternalResource
import java.nio.file.Path
import kotlin.io.path.isExecutable

/**
 * Finds conda on localsystem between python envs installed with script
 *
 * To be fixed: support targets as well
 */
class LocalCondaRule : ExternalResource() {

  lateinit var condaPath: Path
    private set

  private lateinit var autoCloseable: AutoCloseable

  val condaPathOnTarget: FullPathOnTarget get() = condaPath.toString()

  val commandExecutor: TargetCommandExecutor get() = TargetEnvironmentRequestCommandExecutor(LocalTargetEnvironmentRequest())

  val condaCommand: PyCondaCommand get() = PyCondaCommand(condaPathOnTarget, null)


  override fun before() {
    super.before()
    val (condaPathEnv, autoCloseable) = runBlocking { PythonType.Conda.getTestEnvironment().getOrElse { throw AssumptionViolatedException("No conda found, run gradle script to install test env") } }

    condaPath = Path.of(condaPathEnv.fullCondaPathOnTarget)
    if (!condaPath.isExecutable()) {
      throw AssumptionViolatedException("$condaPath is not executable")
    }
    this.autoCloseable = autoCloseable
  }

  override fun after() {
    autoCloseable.close()
  }
}