// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.conda

import com.intellij.execution.processTools.getResultStdout
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.env.python.conda.LocalCondaRule.Companion.CONDA_PATH
import com.jetbrains.python.sdk.add.target.conda.TargetCommandExecutor
import com.jetbrains.python.sdk.add.target.conda.TargetEnvironmentRequestCommandExecutor
import com.jetbrains.python.sdk.add.target.conda.suggestCondaPath
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import kotlinx.coroutines.runBlocking
import org.junit.AssumptionViolatedException
import org.junit.rules.ExternalResource
import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListSet
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

  val commandExecutor: TargetCommandExecutor get() = TargetEnvironmentRequestCommandExecutor(LocalTargetEnvironmentRequest())

  val condaCommand: PyCondaCommand get() = PyCondaCommand(condaPathOnTarget, null)

  private val condasBeforeTest = ConcurrentSkipListSet<String>()

  override fun before() {
    super.before()
    val condaPathEnv = System.getenv()[CONDA_PATH]
                       ?: runBlocking { suggestCondaPath() }
                       ?: throw AssumptionViolatedException("No $CONDA_PATH set")
    condaPath = Path.of(condaPathEnv)
    if (!condaPath.isExecutable()) {
      throw AssumptionViolatedException("$condaPath is not executable")
    }
    runBlocking {
      condasBeforeTest.addAll(getCondaNames())
    }
  }

  override fun after(): Unit = runBlocking {
    val condasToRemove = getCondaNames()
    condasToRemove.removeAll(condasBeforeTest)
    for (envName in condasToRemove) {
      val condaPath = condaPath.toString()
      println("Removing $envName")

      for (arg in arrayOf("--name", "-p")) {
        val args = arrayOf(condaPath, "remove", arg, envName, "--all", "-y")
        Runtime.getRuntime().exec(args).getResultStdout().getOrElse {
          logger<LocalCondaRule>().warn(it)
        }
      }
    }
  }


  private suspend fun getCondaNames() = PyCondaEnv.getEnvs(commandExecutor, condaPathOnTarget).getOrThrow()
    .map { it.envIdentity.userReadableName }
    .toMutableSet()
}