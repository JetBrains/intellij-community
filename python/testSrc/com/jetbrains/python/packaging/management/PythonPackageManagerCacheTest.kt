// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.Disposer
import com.intellij.python.junit5Tests.framework.pyProjectFixture
import com.intellij.python.pyproject.PyDependencyGroup
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.common.PythonOutdatedPackage
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createDirectories
import kotlin.time.Duration.Companion.minutes

/**
 * A package manager of an environment that did not change since its last list takes the packages of that list, see
 * [com.jetbrains.python.packaging.PythonInstalledPackagesCache], and runs no list.
 */
@TestApplication
@Subsystems.Packaging
@Layers.Functional
internal class PythonPackageManagerCacheTest {
  private val projectFixture = projectFixture()
  private val modulePathFixture = tempPathFixture(prefix = "py-packages-cache")
  private val pyProjectFixture = projectFixture.pyProjectFixture(modulePathFixture)

  @Test
  fun `a manager of an environment that did not change runs no list`(@TempDir home: Path): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val sitePackages = home.resolve("lib/python3.12/site-packages").createDirectories()
    sitePackages.resolve("requests-2.31.0.dist-info").createDirectories()
    val interpreter = registerInterpreter(project, home, sitePackages)
    val lists = AtomicInteger()

    val outdated = AtomicInteger()
    val first = CountingManager(project, interpreter, lists)
    first.waitForInit()
    Disposer.dispose(first)
    val second = CountingManager(project, interpreter, lists, outdated)
    second.waitForInit()

    assertThat(lists.get()).describedAs("Only the first manager lists the packages").isEqualTo(1)
    assertThat(outdated.get()).describedAs("A list from the cache starts no check of the outdated packages").isEqualTo(0)
    assertThat(second.listInstalledPackagesSnapshot().map { it.name }).containsExactly("requests")
    Disposer.dispose(second)
  }

  @Test
  fun `an install since the last list makes the next manager list again`(@TempDir home: Path): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val sitePackages = home.resolve("lib/python3.12/site-packages").createDirectories()
    sitePackages.resolve("requests-2.31.0.dist-info").createDirectories()
    val interpreter = registerInterpreter(project, home, sitePackages)
    val lists = AtomicInteger()
    val first = CountingManager(project, interpreter, lists)
    first.waitForInit()
    Disposer.dispose(first)

    sitePackages.resolve("idna-3.6.dist-info").createDirectories()
    val second = CountingManager(project, interpreter, lists)
    second.waitForInit()

    assertThat(lists.get()).isEqualTo(2)
    Disposer.dispose(second)
  }

  @Test
  fun `a list with a package of a subclass is not kept`(@TempDir home: Path): Unit = timeoutRunBlocking(1.minutes) {
    val project = projectFixture.get()
    val sitePackages = home.resolve("lib/python3.12/site-packages").createDirectories()
    val interpreter = registerInterpreter(project, home, sitePackages)
    val lists = AtomicInteger()
    val withSubclass = listOf(object : PythonPackage("conda-only", "1.0", false) {})
    val first = CountingManager(project, interpreter, lists, packages = withSubclass)
    first.waitForInit()
    Disposer.dispose(first)

    val second = CountingManager(project, interpreter, lists, packages = withSubclass)
    second.waitForInit()

    assertThat(lists.get()).describedAs("A list that the cache cannot keep is listed again").isEqualTo(2)
    Disposer.dispose(second)
  }

  /** A non-mock interpreter of the Python project of the fixture, whose `sys.path` is [sitePackages]. */
  private suspend fun registerInterpreter(project: Project, home: Path, sitePackages: Path): PythonInterpreter {
    val sdk = ProjectJdkImpl("py-packages-cache ${home.fileName}", PythonSdkType.getInstance())
    val modificator = sdk.sdkModificator
    modificator.homePath = home.resolve("bin").resolve("python").toString()
    modificator.sdkAdditionalData = PythonSdkAdditionalData(PyFlavorAndData(PyFlavorData.Empty, VirtualEnvSdkFlavor.getInstance()), home)
    WriteAction.runAndWait<RuntimeException> { modificator.commitChanges() }
    val interpreter = PythonInterpreterRegistry.getInstance(project).addMockPythonInterpreter(pyProjectFixture.get(), sdk)
    PythonInterpreterDetails.getInstance(project).writeSysPath(interpreter, listOf(sitePackages.toString()))
    return interpreter
  }

  /** A manager whose list reads one package and counts the lists in [lists]. */
  private class CountingManager(
    project: Project,
    interpreter: PythonInterpreter,
    private val lists: AtomicInteger,
    private val outdated: AtomicInteger = AtomicInteger(),
    private val packages: List<PythonPackage> = listOf(PythonPackage("requests", "2.31.0", false)),
  ) :
    PythonPackageManager(project, interpreter) {
    override val repositoryManager: PythonRepositoryManager = TestPythonRepositoryManager(project)
    override val dependenciesFilesRelativePaths: List<Path> = emptyList()

    override suspend fun loadPackagesCommand(): PyResult<List<PythonPackage>> {
      lists.incrementAndGet()
      return PyResult.success(packages)
    }

    override suspend fun loadOutdatedPackagesCommand(): PyResult<List<PythonOutdatedPackage>> {
      outdated.incrementAndGet()
      return PyResult.success(emptyList())
    }
    override suspend fun syncLockedCommand(): PyResult<Unit> = PyResult.success(Unit)
    override suspend fun installPackageCommand(
      installRequest: PythonPackageInstallRequest,
      options: List<String>,
      module: Module?,
      dependencyGroup: PyDependencyGroup?,
    ): PyResult<Unit> = PyResult.success(Unit)

    override suspend fun updatePackageCommand(vararg specifications: PythonRepositoryPackageSpecification): PyResult<Unit> = PyResult.success(Unit)
    override suspend fun uninstallPackageCommand(
      vararg pythonPackages: String,
      workspaceMember: PyWorkspaceMember?,
      dependencyGroup: PyDependencyGroup?,
    ): PyResult<Unit> = PyResult.success(Unit)
  }
}
