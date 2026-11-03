// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.python.requirements.parser.PyRequirementParser
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.Result
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.cache.firstPageOrEmpty
import com.jetbrains.python.packaging.cache.impl.InMemorySearchPage
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.common.PythonSimplePackageDetails
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.repository.PyPiPackageRepository
import com.jetbrains.python.packaging.repository.PythonRepositoryManagerBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@Subsystems.PackagingRequirements
@Layers.Functional
@TestFor(issues = ["PY-91735"])
internal class PixiPythonRepositoryDelegationTest {
  private val projectFixture = projectFixture()

  @Test
  fun `saved Python repositories retain their shared identity and enabled state`(@TestDisposable disposable: Disposable) {
    val delegate = PythonRepositories(projectFixture.get()).also { Disposer.register(disposable, it) }
    val saved = PyPackageRepository("Saved", "https://saved.example.org/simple", null)
    val disabled = PyPackageRepository("Disabled", "https://disabled.example.org/simple", null).also { it.enabled = false }
    delegate.allRepositories = listOf(PyPiPackageRepository, saved, disabled)
    val manager = repositoryManager(delegate, disposable)

    assertThat(manager.allRepositories).containsExactly(manager.nativeRepository, PyPiPackageRepository, saved, disabled)
    assertThat(manager.repositories).containsExactly(manager.nativeRepository, PyPiPackageRepository, saved)
    assertThat(manager.builtInRepositories).containsExactly(manager.nativeRepository, PyPiPackageRepository)
    val replacement = PyPackageRepository("Replacement", "https://replacement.example.org/simple", null)
    delegate.allRepositories = listOf(PyPiPackageRepository, replacement)
    assertThat(manager.allRepositories).containsExactly(manager.nativeRepository, PyPiPackageRepository, replacement)
    Disposer.dispose(manager)
    assertThat(delegate.disposed).isFalse()
  }

  @Test
  fun `Python details retain the shared repository result`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val delegate = PythonRepositories(projectFixture.get()).also { Disposer.register(disposable, it) }
    val manager = repositoryManager(delegate, disposable)
    val saved = delegate.saved

    val details = manager.getPackageDetails("private-probe", saved).getOrThrow()

    assertThat(details).isSameAs(delegate.details)
    assertThat(delegate.calls).containsExactly("details:private-probe:Saved")
    assertThat(manager.getVersions("private-probe", saved)).containsExactly("3.0", "1.0")
    assertThat(delegate.calls).containsExactly("details:private-probe:Saved", "versions:private-probe:Saved")
  }

  @Test
  fun `Python search uses the shared repository cache`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val delegate = PythonRepositories(projectFixture.get()).also { Disposer.register(disposable, it) }
    val manager = repositoryManager(delegate, disposable)

    val result = manager.searchPackages(delegate.saved, "private", 7)

    assertThat(result.firstPageOrEmpty().getOrThrow()).containsExactly("private-probe")
    assertThat(delegate.calls).containsExactly("search:private:Saved:7")
  }

  @Test
  fun `Python specifications use the shared repository manager`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val delegate = PythonRepositories(projectFixture.get()).also { Disposer.register(disposable, it) }
    val manager = repositoryManager(delegate, disposable)
    val requirement = checkNotNull(PyRequirementParser.fromLine("private-probe>=1", projectFixture.get()))

    val specification = manager.findPackageSpecification(requirement, delegate.saved)

    assertThat(specification?.repository).isSameAs(delegate.saved)
    assertThat(specification?.requirement).isSameAs(requirement)
    assertThat(delegate.calls.filter { it.startsWith("specification:") }).containsExactly("specification:private-probe:Saved")
  }

  @Test
  fun `an installed Python package can resolve through a saved repository`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val delegate = PythonRepositories(projectFixture.get()).also { Disposer.register(disposable, it) }
    val manager = PixiRepositoryManager(
      projectFixture.get(), delegate,
      resolveInstalledPackage = { PyResult.success(PixiPackage("private-probe", "1.0", "pypi")) },
    ) { error("An installed Python package must not search a native channel.") }
      .also { Disposer.register(disposable, it) }
    val requirement = checkNotNull(PyRequirementParser.fromLine("private-probe>=2", projectFixture.get()))

    val specification = manager.findPackageSpecification(requirement, null)

    assertThat(specification?.repository).isSameAs(delegate.saved)
    assertThat(delegate.calls.filter { it.startsWith("specification:") }).containsExactly("specification:private-probe:null")
  }

  @Test
  fun `cache updates use the shared Python repository manager`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val delegate = PythonRepositories(projectFixture.get()).also { Disposer.register(disposable, it) }
    val manager = repositoryManager(delegate, disposable)

    manager.initCaches().getOrThrow()
    manager.refreshCaches().getOrThrow()

    assertThat(delegate.calls).containsExactly("init", "refresh")
  }

  @Test
  fun `Python updates select the latest version from enabled saved repositories`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val delegate = PythonRepositories(projectFixture.get()).also { Disposer.register(disposable, it) }
    val disabled = PyPackageRepository("Disabled", "https://disabled.example.org/simple", null).also { it.enabled = false }
    delegate.allRepositories = listOf(PyPiPackageRepository, delegate.saved, disabled)
    delegate.versions[PyPiPackageRepository] = listOf("2.0")
    delegate.versions[disabled] = listOf("99.0")

    val update = findPixiPythonPackageUpdate(PixiPackage("private-probe", "1.0", "pypi"), delegate)

    assertThat(update?.latestVersion).isEqualTo("3.0")
    assertThat(delegate.calls.filter { it.startsWith("versions:") })
      .containsExactly("versions:private-probe:PyPI", "versions:private-probe:Saved")
  }

  @Test
  fun `a saved repository can update a package absent from PyPI`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val delegate = PythonRepositories(projectFixture.get()).also { Disposer.register(disposable, it) }
    delegate.versions[PyPiPackageRepository] = emptyList()

    val update = findPixiPythonPackageUpdate(PixiPackage("private-probe", "1.0", "pypi"), delegate)

    assertThat(update?.latestVersion).isEqualTo("3.0")
  }

  private fun repositoryManager(delegate: PythonRepositoryManager, disposable: Disposable): PixiRepositoryManager =
    PixiRepositoryManager(projectFixture.get(), pythonRepositoryManager = delegate) {
      error("A Python repository operation must not search a native channel.")
    }.also { Disposer.register(disposable, it) }

  private class PythonRepositories(override val project: Project) : PythonRepositoryManagerBase() {
    val saved = PyPackageRepository("Saved", "https://saved.example.org/simple", null)
    override var allRepositories: List<PyPackageRepository> = listOf(PyPiPackageRepository, saved)
    val calls = mutableListOf<String>()
    val versions = mutableMapOf(saved to listOf("3.0", "1.0"))
    val details = PythonSimplePackageDetails("private-probe", listOf("3.0", "1.0"), saved, summary = "The saved summary.")
    var disposed = false
      private set

    override fun dispose() {
      disposed = true
    }

    override suspend fun getPackageDetails(packageName: String, repository: PyPackageRepository?): PyResult<PythonPackageDetails> {
      calls.add("details:$packageName:${repository?.name}")
      return PyResult.success(details)
    }

    override suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String>? {
      calls.add("versions:$packageName:${repository?.name}")
      return versions[repository]
    }

    override suspend fun searchPackages(repository: PyPackageRepository, needle: String, pageSize: Int): PythonPackageSearchResult {
      calls.add("search:$needle:${repository.name}:$pageSize")
      return InMemorySearchPage.resultFromMatches(listOf("private-probe"), pageSize)
    }

    override suspend fun findPackageSpecification(
      requirement: PyRequirement, repository: PyPackageRepository?,
    ): PythonRepositoryPackageSpecification {
      calls.add("specification:${requirement.name}:${repository?.name}")
      return PythonRepositoryPackageSpecification(repository ?: saved, requirement)
    }

    override suspend fun initCaches(): Result<Unit, PythonRepositoryManager.PythonRepositoryIOError> {
      calls.add("init")
      return Result.Success(Unit)
    }

    override suspend fun refreshCaches(): Result<Unit, PythonRepositoryManager.PythonRepositoryIOError> {
      calls.add("refresh")
      return Result.Success(Unit)
    }
  }
}
