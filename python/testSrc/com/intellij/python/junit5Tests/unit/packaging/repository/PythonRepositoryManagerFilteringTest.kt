// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.repository

import com.intellij.openapi.project.Project
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageName
import com.intellij.python.requirements.PyPackageVersion
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.cache.PythonPackageSearchPage
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.packaging.common.PythonRepositoryPackageSpecification
import com.jetbrains.python.packaging.management.PythonRepositoryManager
import com.jetbrains.python.packaging.repository.PyPackageRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PY-91041: disabled repositories used to keep contributing packages to the PPTW list and the
 * install-dialog search because `searchPackages` / `hasPackageSnapshot` iterated the raw
 * repository list without checking [PyPackageRepository.enabled]. The fix promoted the filter
 * into the interface — the `repositories` property is a default getter over
 * `allRepositories.filter { it.enabled }` — so every query path picks up the exclusion
 * automatically.
 *
 * These tests pin the resulting contract:
 *  - the default `repositories` getter hides disabled entries;
 *  - `searchPackages(needle)` returns only enabled repositories;
 *  - `hasPackageSnapshot(name)` short-circuits over disabled repositories;
 *  - toggling `enabled` at runtime is picked up without rebuilding the manager.
 */
internal class PythonRepositoryManagerFilteringTest {

  @Test
  fun `default repositories getter filters out disabled entries`() {
    val enabled = fakeRepo("PyPI", packages = listOf("requests"), enabled = true)
    val disabled = fakeRepo("Internal", packages = listOf("internal-tool"), enabled = false)
    val manager = FilteringFakeRepositoryManager(listOf(enabled, disabled))

    assertEquals(listOf<PyPackageRepository>(enabled, disabled), manager.allRepositories, "raw list is the source of truth")
    assertEquals(listOf<PyPackageRepository>(enabled), manager.repositories, "filtered view hides disabled entries")
  }

  @Test
  fun `searchPackages skips disabled repositories`() {
    val enabled = fakeRepo("PyPI", packages = listOf("requests", "requests-mock"), enabled = true)
    val disabled = fakeRepo("Internal", packages = listOf("requests-internal"), enabled = false)
    val manager = FilteringFakeRepositoryManager(listOf(enabled, disabled))

    val hits = manager.searchPackages(needle = "requests")

    assertEquals(setOf<PyPackageRepository>(enabled), hits.keys, "disabled repo must not appear in search results")
    assertFalse(disabled in hits.keys, "regression guard for PY-91041")
    assertEquals(listOf("requests", "requests-mock"), hits.getValue(enabled).firstPage())
  }

  @Test
  fun `hasPackageSnapshot short-circuits over disabled repositories`() {
    val enabled = fakeRepo("PyPI", packages = listOf("flask"), enabled = true)
    val disabled = fakeRepo("Internal", packages = listOf("internal-only"), enabled = false)
    val manager = FilteringFakeRepositoryManager(listOf(enabled, disabled))

    assertTrue(manager.hasPackageSnapshot("flask"), "enabled repo still contributes existence checks")
    assertFalse(manager.hasPackageSnapshot("internal-only"), "disabled repo must not answer existence checks")
  }

  @Test
  fun `re-enabling a repository restores its results without rebuilding the manager`() {
    val toggle = fakeRepo("Toggle", packages = listOf("secret"), enabled = false)
    val manager = FilteringFakeRepositoryManager(listOf(toggle))

    assertTrue(manager.repositories.isEmpty(), "disabled at start — nothing to search")
    assertFalse(manager.hasPackageSnapshot("secret"))

    toggle.enabled = true

    assertEquals(listOf<PyPackageRepository>(toggle), manager.repositories, "flip flows through the default filter")
    assertTrue(manager.hasPackageSnapshot("secret"), "flip is picked up on the next query")
  }

  private fun fakeRepo(name: String, packages: List<String>, enabled: Boolean): ToggleableFakeRepository =
    ToggleableFakeRepository(name, url = "https://$name.example.test/simple", enabled = enabled, packages = packages)

  private fun PythonPackageSearchResult.firstPage(): List<String> {
    val page = pages.firstOrNull() ?: return emptyList()
    val result = page.contents()
    return (result as? Result.Success<List<String>>)?.result ?: emptyList()
  }
}

/**
 * Test-only [PythonRepositoryManager] that returns its constructor argument verbatim as
 * [allRepositories]. Everything else is left to the interface defaults — that is the whole
 * point of the test: the default `repositories` / `searchPackages` / `hasPackageSnapshot`
 * paths must apply the `enabled` filter without any help from the implementation.
 */
private class FilteringFakeRepositoryManager(
  override val allRepositories: List<PyPackageRepository>,
) : PythonRepositoryManager {
  override val project: Project get() = error("Project unused in this test")

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

/**
 * Minimal test repository — an in-memory package set searched by prefix normalisation, plus a
 * flip-able `enabled` flag to exercise runtime toggles without going through the persistent
 * `PyPackageRepositories` service. Distinct name from the aggregator test's `FakeRepository`
 * to avoid a same-package top-level clash.
 */
private class ToggleableFakeRepository(
  name: String,
  url: String,
  enabled: Boolean,
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

  override fun hasPackage(name: String): Boolean = packages.contains(name)
}
