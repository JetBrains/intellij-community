// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.pixi

import com.intellij.idea.TestFor
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.pixi.test.env.PixiPyEnvironment
import com.intellij.python.pixi.test.env.PixiPyEnvironmentSpec
import com.intellij.python.pixi.test.env.pixiEnvironment
import com.intellij.python.pytools.backend.getToolVersion
import com.intellij.python.test.env.core.PyEnvironmentFactory
import com.intellij.python.test.env.junit5.PythonFactoryExtension
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Verifies a pinned Pixi executable and environment. */
@Subsystems.Interpreters
@Layers.Functional
@TestApplication
@TestFor(issues = ["PY-91711"])
@Timeout(value = 11, unit = TimeUnit.MINUTES)
@ExtendWith(PythonFactoryExtension::class)
internal class PixiPyToolEnvTest(private val factory: PyEnvironmentFactory) {
  @Test
  fun `the pinned Pixi executable reports its version`() =
    factory.testWithEnv(environmentSpec) { environment ->
      val executable = environment.pixiExecutable
      val executableVersion = BinOnEel(executable).getToolVersion("pixi").getOrThrow().value

      assertThat(executableVersion).isEqualTo(PIXI_TEST_VERSION)
    }

  @Test
  fun `the Pixi environment provides Python and installs PyPI libraries`() =
    factory.testWithEnv(environmentSpec) { environment ->
      val requestsVersion = ExecService().execGetStdout(
        environment.pythonPath,
        Args("-c", "import requests; print(requests.__version__)"),
      ).getOrThrow().trim()

      assertThat(requestsVersion).isEqualTo("2.32.3")
    }

  companion object {
    private val environmentSpec = pixiEnvironment(PIXI_TEST_VERSION) {
      pythonVersion = pythonVersion("3.13")
      libraries {
        +"requests==2.32.3"
      }
    }

    private fun PyEnvironmentFactory.testWithEnv(
      spec: PixiPyEnvironmentSpec,
      timeout: Duration = 10.seconds,
      test: suspend (PixiPyEnvironment) -> Unit,
    ) {
      val environment = timeoutRunBlocking(10.minutes) {
        createEnvironment(spec)
      }
      val pixiEnvironment = environment.unwrap<PixiPyEnvironment>()
      if (pixiEnvironment == null) {
        error("The Pixi specification did not create a Pixi environment")
      }
      timeoutRunBlocking(timeout) {
        test(pixiEnvironment)
      }
    }
  }
}

internal const val PIXI_TEST_VERSION = "0.78.0"
