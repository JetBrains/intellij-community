// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.python.sdk.backend.removeTestSdkFromOpenProjects
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.community.junit5Tests.framework.conda.CondaEnv
import com.intellij.python.community.junit5Tests.framework.conda.PyEnvTestCaseWithConda
import com.intellij.python.requirements.pyRequirementVersionSpec
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.pythonInterpreterResultAsync
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.conda.CondaPackage
import com.jetbrains.python.packaging.conda.CondaPackageManager
import com.jetbrains.python.packaging.management.PythonPackageInstallRequest
import com.jetbrains.python.packaging.management.findPackageSpecification
import com.jetbrains.python.packaging.requirement.PyRequirementRelation
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * PY-93044: conda 27.9 stops adding pip to every env with Python (`add_pip_as_python_dependency` defaults to false,
 * conda/conda#16404). The env PyCharm creates must still contain pip, or PyPI packages cannot be installed and the
 * outdated check fails. The tests set the option to false in the `.condarc` of the base install, as conda 27.9 behaves by default.
 */
@PyEnvTestCaseWithConda
internal class CondaEnvWithoutImplicitPipTest {
  private val projectFixture = projectFixture()

  private lateinit var conda: String
  private lateinit var condarc: Path
  private var originalCondarc: String? = null
  private val sdks = mutableListOf<Sdk>()
  private val namedEnvs = mutableListOf<String>()

  @BeforeEach
  fun setUp(@CondaEnv baseEnv: PyCondaEnv): Unit = timeoutRunBlocking {
    conda = baseEnv.fullCondaPathOnTarget
    // <root>/bin/conda on Unix; <root>/condabin/conda.bat or <root>/Scripts/conda.exe on Windows. Conda reads <root>/.condarc for
    // every command of this install. The install is shared by other tests, so the file is restored in tearDown.
    condarc = Path.of(conda).parent.parent.resolve(".condarc")
    withContext(Dispatchers.IO) {
      originalCondarc = if (condarc.exists()) condarc.readText() else null
      val kept = originalCondarc?.lines()?.filterNot { it.trimStart().startsWith(ADD_PIP_OPTION) }?.joinToString("\n").orEmpty()
      condarc.writeText("$kept\n$ADD_PIP_OPTION: false\n")
    }
  }

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking(10.minutes) {
    sdks.forEach { removeTestSdkFromOpenProjects(it) }
    for (name in namedEnvs) {
      runConda("env", "remove", "-y", "-n", name)
    }
    withContext(Dispatchers.IO) {
      val original = originalCondarc
      if (original == null) Files.deleteIfExists(condarc) else condarc.writeText(original)
    }
  }

  @Test
  fun `unnamed env created by PyCharm contains pip`(@TempDir tempDir: Path): Unit = timeoutRunBlocking(10.minutes) {
    val manager = createUnnamedEnv(tempDir.resolve("env"))
    assertCondaManagedPip(manager)
  }

  @Test
  fun `named env created by PyCharm contains pip`(): Unit = timeoutRunBlocking(10.minutes) {
    val name = "py93044_${System.currentTimeMillis()}"
    namedEnvs += name
    PyCondaEnv.createEnv(PyCondaCommand(conda, null), NewCondaEnvRequest.EmptyNamedEnv(LanguageLevel.PYTHON311, name)).getOrThrow()

    val manager = packageManager(PyCondaEnv(PyCondaEnvIdentity.NamedEnv(name), conda))
    assertCondaManagedPip(manager)
  }

  @Test
  fun `PyPI package installs into an env created by PyCharm`(@TempDir tempDir: Path): Unit = timeoutRunBlocking(15.minutes) {
    val envPath = tempDir.resolve("env")
    val manager = createUnnamedEnv(envPath)
    val spec = manager.findPackageSpecification("yandex-book-api", pyRequirementVersionSpec(PyRequirementRelation.EQ, "0.1.0"))
               ?: error("Cannot create package specification for yandex-book-api 0.1.0")

    manager.installPackage(PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(spec))).getOrThrow()

    val (exitCode, output) = runConda("run", "-p", envPath.pathString, "python", "-m", "pip", "show", "yandex-book-api")
    assertEquals(0, exitCode, "pip show yandex-book-api failed:\n$output")
  }

  @Test
  fun `outdated check reports conda packages in an env created by PyCharm`(@TempDir tempDir: Path): Unit = timeoutRunBlocking(15.minutes) {
    val manager = createUnnamedEnv(tempDir.resolve("env"))
    val spec = manager.findPackageSpecification("pytest", pyRequirementVersionSpec(PyRequirementRelation.EQ, "7.4.0"))
               ?: error("Cannot create package specification for pytest 7.4.0")

    manager.installPackage(PythonPackageInstallRequest.ByRepositoryPythonPackageSpecifications(listOf(spec))).getOrThrow()

    // The manager reloads the outdated packages in the background after the install.
    var outdated = manager.listOutdatedPackagesSnapshot()
    repeat(36) {
      if ("pytest" in outdated) return@repeat
      delay(5.seconds)
      outdated = manager.listOutdatedPackagesSnapshot()
    }
    assertTrue("pytest" in outdated, "pytest 7.4.0 must be reported as outdated, outdated packages: ${outdated.keys}")
  }

  private suspend fun createUnnamedEnv(envPath: Path): CondaPackageManager {
    PyCondaEnv.createEnv(PyCondaCommand(conda, null), NewCondaEnvRequest.EmptyUnnamedEnv(LanguageLevel.PYTHON311, envPath.pathString)).getOrThrow()
    return packageManager(PyCondaEnv(PyCondaEnvIdentity.UnnamedEnv(envPath, false), conda))
  }

  private suspend fun packageManager(env: PyCondaEnv): CondaPackageManager {
    val project = projectFixture.get()
    val sdk = env.createSdkFromThisEnv(project, null, project.basePath?.toNioPathOrNull()!!).getOrThrow().getSdkAPI()
    sdks += sdk
    return CondaPackageManager(project, sdk.pythonInterpreterResultAsync().orThrow()).also {
      Disposer.register(project, it)
      it.waitForInit()
    }
  }

  private suspend fun assertCondaManagedPip(manager: CondaPackageManager) {
    val pip = manager.listInstalledPackages().firstOrNull { it.name == "pip" }
    assertTrue(pip is CondaPackage && !pip.installedWithPip, "The env must contain pip installed by conda, got $pip")
  }

  private suspend fun runConda(vararg args: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val command = if (SystemInfo.isWindows && conda.endsWith(".bat")) listOf("cmd", "/c", conda) + args else listOf(conda) + args
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(10, TimeUnit.MINUTES)) process.destroyForcibly()
    process.exitValue() to output
  }

  private companion object {
    const val ADD_PIP_OPTION = "add_pip_as_python_dependency"
  }
}
