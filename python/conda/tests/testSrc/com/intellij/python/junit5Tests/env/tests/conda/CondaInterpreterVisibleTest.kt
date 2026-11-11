// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.community.junit5Tests.framework.conda.createCondaEnv
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.model.evolution.getInterpreter
import com.intellij.python.pyproject.model.evolution.setPythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.removeTestSdkFromOpenProjects
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/**
 * A conda environment that the interpreter settings make is the interpreter of the module as soon as it is set: the
 * environment is in the interpreter store when its creation returns, so nothing reports it as not found, see PY-93079.
 */
@RegistryKeyAppLevel(key = "python.interpreter.library.entries", value = "true")
@PyEnvTestCaseWithConda
class CondaInterpreterVisibleTest {
  private val projectFixture = projectFixture()
  private val pyProject = projectFixture.pyProjectFixture()

  @Test
  fun `the created conda environment is the interpreter of its module at once`(@CondaEnv condaEnv: PyCondaEnv, @TempDir path: Path): Unit =
    timeoutRunBlocking(10.minutes) {
      val pyProject = pyProject.get()
      val env = createCondaEnv(condaEnv, path.resolve("conda_of_the_project"))

      val created = env.createSdkFromThisEnv(pyProject.project, null, pyProject.baseDir).getOrThrow()

      try {
        pyProject.setPythonInterpreter(created).orThrow()
        assertThat(pyProject.getInterpreter())
          .describedAs("the module names the environment the creation returned, with no wait for a scan of the conda directory")
          .isEqualTo(created)
      }
      finally {
        removeTestSdkFromOpenProjects(created.getSdkAPI())
      }
    }
}
