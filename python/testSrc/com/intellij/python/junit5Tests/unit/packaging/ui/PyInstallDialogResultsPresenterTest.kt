// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.ui

import com.jetbrains.python.Result
import com.jetbrains.python.packaging.cache.PythonPackageSearchPage
import com.jetbrains.python.packaging.cache.PythonPackageSearchResult
import com.jetbrains.python.packaging.repository.PyPackageRepository
import com.jetbrains.python.packaging.toolwindow.ui.PackageLeafNode
import com.jetbrains.python.packaging.toolwindow.ui.buildInstallDialogResults
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PyInstallDialogResultsPresenterTest {

  private fun repo(name: String, url: String = "https://$name/simple"): PyPackageRepository =
    PyPackageRepository(name, url, null)

  private fun page(vararg names: String): PythonPackageSearchPage = object : PythonPackageSearchPage {
    override fun contents(): Result<List<String>, PythonPackageSearchPage.DataInvalidatedError> =
      Result.Success(names.toList())
  }

  private val invalidatedPage: PythonPackageSearchPage = object : PythonPackageSearchPage {
    override fun contents(): Result<List<String>, PythonPackageSearchPage.DataInvalidatedError> =
      Result.Failure(PythonPackageSearchPage.DataInvalidatedError)
  }

  private fun result(vararg pages: PythonPackageSearchPage, pageSize: Int = 50): PythonPackageSearchResult =
    PythonPackageSearchResult(total = pages.sumOf { (it.contents() as? Result.Success)?.result?.size ?: 0 },
                              pages = pages.toList(),
                              maxPageSize = pageSize)

  @Test
  fun `exact match beats prefix which beats substring`() {
    val pypi = repo("pypi")
    val results = mapOf(pypi to result(page("djangorestframework", "django", "somedjango")))

    val nodes = buildInstallDialogResults(results, managedRepositories = setOf(pypi), query = "django")

    assertEquals(
      listOf("django", "djangorestframework", "somedjango"),
      nodes.map { it.packageName },
      "exact > shortest prefix > substring (managed repos: all page rows kept)",
    )
    assertTrue(nodes.all { it.repoName == "pypi" })
  }

  @Test
  fun `extra repositories are filtered by containsIgnoreCase against the query`() {
    // Extra (user-configured) repos return their full index without server-side query filtering,
    // so the presenter has to drop rows that don't contain the query. Managed repos pre-filter
    // upstream, so their rows are kept verbatim (that's how "somedjango" survives above).
    val managed = repo("pypi")
    val extra = repo("mirror", url = "https://internal/simple")
    val results = mapOf(
      managed to result(page("django")),
      extra to result(page("django-extensions", "requests", "urllib3")),
    )

    val nodes = buildInstallDialogResults(results, managedRepositories = setOf(managed), query = "django")

    assertEquals(setOf("django", "django-extensions"), nodes.map { it.packageName }.toSet())
    assertEquals(setOf("pypi", "mirror"), nodes.map { it.repoName }.toSet())
  }

  @Test
  fun `invalidated pages are skipped, other pages of the same repo still contribute`() {
    val pypi = repo("pypi")
    val results = mapOf(pypi to result(invalidatedPage, page("django")))

    val nodes = buildInstallDialogResults(results, managedRepositories = setOf(pypi), query = "django")

    assertEquals(listOf(PackageLeafNode("django", "pypi")), nodes)
  }

  @Test
  fun `empty search results produce empty output`() {
    val nodes = buildInstallDialogResults(results = emptyMap(), managedRepositories = emptySet(), query = "anything")
    assertTrue(nodes.isEmpty())
  }

  @Test
  fun `ties on the priority key fall back to lexicographic name order`() {
    val pypi = repo("pypi")
    val results = mapOf(pypi to result(page("charlie-lib", "alpha-lib", "bravo-lib")))

    // None start with "zzz"; every entry lands in the "else -> 0" bucket, so the .thenBy { it }
    // stable-tiebreaker in createNameComparator drives the final order.
    val nodes = buildInstallDialogResults(results, managedRepositories = setOf(pypi), query = "zzz")

    assertEquals(listOf("alpha-lib", "bravo-lib", "charlie-lib"), nodes.map { it.packageName })
  }
}
