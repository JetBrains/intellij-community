// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.repository

import com.intellij.openapi.project.Project
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.PyPackageVersion
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.cache.PythonPackageSearchPage
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.ui.aggregateInstallDialogSearch
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeRepository(
  name: String,
  url: String,
  enabled: Boolean = true,
  private val packages: List<String>,
) : PyPackageRepository(name, url, null) {

  init {
    this.enabled = enabled
  }

  override fun search(needle: String, pageSize: Int): PythonPackageSearchResult {
    val normalized = PyPackageName.normalizePackageName(needle)
    val matches = packages.filter { it.contains(normalized, ignoreCase = true) }
    return PythonPackageSearchResult(
      total = matches.size,
      pages = listOf(object : PythonPackageSearchPage {
        override fun contents(): Result<List<String>, PythonPackageSearchPage.DataInvalidatedError> =
          Result.Success(matches)
      }),
      maxPageSize = pageSize,
    )
  }
}

private class FakeRepositoryManager(
  override val repositories: List<PyPackageRepository>,
  var awaitReadyCalled: Boolean = false,
) : PythonRepositoryManager {
  override val project: Project get() = error("Project unused in this test")

  override suspend fun awaitReady() {
    awaitReadyCalled = true
  }

  override suspend fun getPackageDetails(packageName: String, repository: PyPackageRepository?): PyResult<PythonPackageDetails> =
    error("not used in this test")
  override suspend fun getLatestVersion(packageName: String, repository: PyPackageRepository?): PyPackageVersion? =
    error("not used in this test")
  override suspend fun getVersions(packageName: String, repository: PyPackageRepository?): List<String>? =
    error("not used in this test")
  override suspend fun refreshCaches() = error("not used in this test")
  override suspend fun initCaches() = error("not used in this test")
  override suspend fun findPackageSpecification(requirement: PyRequirement, repository: PyPackageRepository?): PythonRepositoryPackageSpecification? =
    error("not used in this test")
}

private fun PythonPackageSearchResult.firstPage(): List<String> =
  pages.firstOrNull()?.contents()?.let { result ->
    when (result) {
      is Result.Success -> result.result
      is Result.Failure -> emptyList()
    }
  } ?: emptyList()

internal class PyInstallDialogSearchAggregatorTest {

  @Test
  fun `awaitReady is called before searching`() = runTest {
    val manager = FakeRepositoryManager(repositories = emptyList())

    aggregateInstallDialogSearch(manager, extraRepositories = emptyList(), query = "anything")

    assertTrue(manager.awaitReadyCalled, "aggregator must wait for manager init before searching")
  }

  @Test
  fun `built-in repositories are searched through the manager`() = runTest {
    val pypi = FakeRepository("PyPI", "https://pypi.org/simple", packages = listOf("requests", "requests-mock", "flask"))
    val manager = FakeRepositoryManager(repositories = listOf(pypi))

    val result = aggregateInstallDialogSearch(manager, extraRepositories = emptyList(), query = "request")

    assertEquals(setOf(pypi), result.keys)
    assertEquals(listOf("requests", "requests-mock"), result.getValue(pypi).firstPage())
  }

  @Test
  fun `enabled extra repository not already covered is searched directly`() = runTest {
    val pypi = FakeRepository("PyPI", "https://pypi.org/simple", packages = listOf("flask"))
    val internalRepo = FakeRepository("Internal", "https://repo.internal/simple", packages = listOf("internal-tool", "unrelated"))
    val manager = FakeRepositoryManager(repositories = listOf(pypi))

    val result = aggregateInstallDialogSearch(manager, extraRepositories = listOf(internalRepo), query = "internal")

    assertTrue(internalRepo in result.keys, "extra repo should appear in result")
    assertEquals(listOf("internal-tool"), result.getValue(internalRepo).firstPage())
  }

  @Test
  fun `extra repository with same url as a built-in is not searched twice`() = runTest {
    val pypi = FakeRepository("PyPI", "https://pypi.org/simple", packages = listOf("requests"))
    val duplicate = FakeRepository("PyPI mirror", "https://pypi.org/simple", packages = listOf("requests"))
    val manager = FakeRepositoryManager(repositories = listOf(pypi))

    val result = aggregateInstallDialogSearch(manager, extraRepositories = listOf(duplicate), query = "requests")

    assertEquals(setOf(pypi), result.keys, "duplicate URL must be deduplicated")
    assertFalse(duplicate in result.keys)
  }

  @Test
  fun `disabled extra repository is skipped`() = runTest {
    val internalRepo = FakeRepository("Internal", "https://repo.internal/simple", enabled = false, packages = listOf("internal-tool"))
    val manager = FakeRepositoryManager(repositories = emptyList())

    val result = aggregateInstallDialogSearch(manager, extraRepositories = listOf(internalRepo), query = "internal")

    assertTrue(result.isEmpty(), "disabled extra repos must not contribute results")
  }
}
