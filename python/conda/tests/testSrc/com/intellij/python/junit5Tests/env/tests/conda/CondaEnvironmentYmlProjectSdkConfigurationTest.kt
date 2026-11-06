package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.delete
import com.jetbrains.python.conda.savePythonCondaPath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@PyEnvTestCase
internal class CondaEnvironmentYmlProjectSdkConfigurationTest {
  private val projectFixture = projectFixture()
  private val modulePathFixture = tempPathFixture(prefix = "conda-module")
  private val pyProjectFixture = projectFixture.pyProjectFixture(modulePathFixture)

  @Test
  fun `conda environment project intent is returned for module`() = timeoutRunBlocking(60.seconds) {
    val cwd = modulePathFixture.get()

    val env = cwd.resolve("environment.yml")
    if (!Files.exists(env)) {
      Files.createFile(env)
    }
    env.writeText(
      """
      name: test-env
      channels:
        - defaults
      dependencies:
        - python
      """.trimIndent()
    )

    val conda = Files.createTempFile("fake-conda", "").toFile().apply {
      setExecutable(true)
    }
    Files.writeString(conda.toPath(), "#!/bin/sh\nexit 0\n")
    savePythonCondaPath(conda.toPath())

    val createSdkInfo = PyProjectSdkConfigurationExtension.findAllSorted(pyProjectFixture.get()).first()

    assertNotNull(createSdkInfo, "Expected conda SDK configuration extension to be found")
    assertEquals(createSdkInfo.toolId.id, "Conda", "Expected to use Conda tool for sdk configuration")
  }

  @Test
  fun `conda environment project intent is returned for module without venv`() = timeoutRunBlocking(60.seconds) {
    val cwd = modulePathFixture.get()

    val env = cwd.resolve("environment.yml")
    if (!Files.exists(env)) {
      Files.createFile(env)
    }
    env.writeText(
      """
      name: test-env
      channels:
        - defaults
      dependencies:
        - python
      """.trimIndent()
    )

    val venvDir = cwd.resolve(".venv")
    if (!Files.exists(venvDir)) {
      Files.createDirectory(venvDir)
    }
    venvDir.delete()

    val conda = Files.createTempFile("fake-conda", "").toFile().apply {
      setExecutable(true)
    }
    Files.writeString(conda.toPath(), "#!/bin/sh\nexit 0\n")
    savePythonCondaPath(conda.toPath())

    val createSdkInfo = PyProjectSdkConfigurationExtension.findAllSorted(pyProjectFixture.get()).first()

    assertNotNull(createSdkInfo, "Expected conda SDK configuration extension to be found after deleting .venv")
    assertEquals(createSdkInfo.toolId.id, "Conda", "Expected to use Conda tool for sdk configuration after deleting .venv")
  }
}
