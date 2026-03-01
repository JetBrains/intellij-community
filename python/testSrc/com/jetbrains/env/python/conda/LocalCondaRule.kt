// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.conda

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.test.env.common.PredefinedPyEnvironments
import com.intellij.python.test.env.common.createEnvironment
import com.intellij.python.test.env.conda.CondaPyEnvironment
import com.intellij.python.test.env.junit4.JUnit4FactoryHolder
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.add.v2.Version
import com.jetbrains.python.sdk.add.v2.conda.getCondaVersion
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

  lateinit var condaVersion: Version
    private set

  private lateinit var autoCloseable: AutoCloseable

  val condaPathOnTarget: FullPathOnTarget get() = condaPath.toString()

  val condaCommand: PyCondaCommand get() = PyCondaCommand(condaPathOnTarget, null)


  override fun before() {
    super.before()
    val env = runBlocking {
      JUnit4FactoryHolder.getOrCreate().createEnvironment(PredefinedPyEnvironments.CONDA).unwrap<CondaPyEnvironment>() ?: error("No conda found")
    }
    condaPath = env.condaExecutable
    if (!condaPath.isExecutable()) {
      throw AssumptionViolatedException("$condaPath is not executable")
    }
    condaVersion = runBlocking { BinOnEel(condaPath).getCondaVersion().getOrThrow() }

    this.autoCloseable = env
  }

  override fun after() {
    autoCloseable.close()
  }

  fun getCondaBinaryToExec(): BinaryToExec {
    return BinOnEel(Path.of(condaPathOnTarget))
  }
}