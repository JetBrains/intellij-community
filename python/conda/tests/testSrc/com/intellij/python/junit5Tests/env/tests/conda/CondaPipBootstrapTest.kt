// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.python.sdk.backend.removeTestSdkFromOpenProjects
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.PyPackageNames
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.conda.CondaPackageManager
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

/**
 * PY-93045: installing a PyPI package into an interpreter without pip first installs pip from the wheel bundled in the helpers.
 * On Windows pip refused to install itself when run as `<wheel>/pip`, so the install failed there. The env is created by conda
 * directly, as a user does in a terminal, and pip is removed from it.
 */
@PyEnvTestCaseWithConda
internal class CondaPipBootstrapTest {
  private val projectFixture = projectFixture()
  private var sdk: Sdk? = null

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    sdk?.let { removeTestSdkFromOpenProjects(it) }
  }

  @Test
  fun `PyPI package installs into a conda env without pip`(@CondaEnv baseEnv: PyCondaEnv, @TempDir tempDir: Path): Unit =
    timeoutRunBlocking(15.minutes) {
      val conda = baseEnv.fullCondaPathOnTarget
      val envPath = tempDir.resolve("env")
      runConda(conda, "create", "-y", "-p", envPath.pathString, "python=3.11").assertSuccess("conda create")
      runConda(conda, "remove", "-y", "-p", envPath.pathString, "--force", PyPackageNames.PIP).assertSuccess("conda remove pip")

      val project = projectFixture.get()
      val env = PyCondaEnv(PyCondaEnvIdentity.UnnamedEnv(envPath, false), conda)
      val condaSdk = env.createSdkFromThisEnv(project, null, project.basePath?.toNioPathOrNull()!!).getOrThrow().getSdkAPI()
      sdk = condaSdk
      val manager = CondaPackageManager(project, condaSdk.pythonInterpreterResultAsync().orThrow()).also { Disposer.register(project, it) }

      val spec = PythonRepositoryPackageSpecification(PyPiPackageRepository, "yandex-book-api", "0.1.0")
      manager.installPackage(PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(spec))).getOrThrow()

      runConda(conda, "run", "-p", envPath.pathString, "python", "-m", "pip", "show", "yandex-book-api").assertSuccess("pip show yandex-book-api")
      assertTrue(manager.listInstalledPackages().any { it.name == PyPackageNames.PIP }, "pip is not bootstrapped")
    }

  private suspend fun runConda(conda: String, vararg args: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val command = if (SystemInfo.isWindows && conda.endsWith(".bat")) listOf("cmd", "/c", conda) + args else listOf(conda) + args
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(10, TimeUnit.MINUTES)) process.destroyForcibly()
    process.exitValue() to output
  }

  private fun Pair<Int, String>.assertSuccess(what: String) = assertEquals(0, first, "$what failed:\n$second")
}
