// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.repository

import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.packaging.PyPackageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class SyncRepositoriesTest {
  private lateinit var packageService: PyPackageService
  private lateinit var repositories: MutableList<PyPackageRepository>
  private lateinit var invalidRepositories: MutableSet<PyPackageRepository>

  @BeforeEach
  fun setUp() {
    packageService = PyPackageService.getInstance()
    packageService.additionalRepositories.clear()
    repositories = mutableListOf()
    invalidRepositories = mutableSetOf()
  }

  @AfterEach
  fun tearDown() {
    packageService.additionalRepositories.clear()
  }

  @Test
  fun `test adds new valid repository`() {
    repositories.add(PyPackageRepository("my-repo", "https://example.com/simple/", null))

    syncRepositories(repositories, invalidRepositories, packageService)

    assertTrue(packageService.additionalRepositories.contains("https://example.com/simple/"))
  }

  @Test
  fun `test removes stale repository`() {
    packageService.addRepository("https://stale.example.com/simple/")

    syncRepositories(repositories, invalidRepositories, packageService)

    assertTrue(packageService.additionalRepositories.isEmpty())
  }

  @Test
  fun `test null url goes to invalid without NPE`() {
    repositories.add(PyPackageRepository("null-url", null, null))

    syncRepositories(repositories, invalidRepositories, packageService)

    assertEquals(1, invalidRepositories.size)
    assertEquals("null-url", invalidRepositories.first().name)
    assertTrue(packageService.additionalRepositories.isEmpty())
  }

  @Test
  fun `test blank url goes to invalid`() {
    repositories.add(PyPackageRepository("blank-url", "  ", null))

    syncRepositories(repositories, invalidRepositories, packageService)

    assertEquals(1, invalidRepositories.size)
    assertEquals("blank-url", invalidRepositories.first().name)
  }

  @Test
  fun `test mixed valid and invalid`() {
    repositories.add(PyPackageRepository("good", "https://ok.example.com/simple/", null))
    repositories.add(PyPackageRepository("null-url", null, null))
    repositories.add(PyPackageRepository("blank-url", "", null))

    syncRepositories(repositories, invalidRepositories, packageService)

    assertTrue(packageService.additionalRepositories.contains("https://ok.example.com/simple/"))
    assertEquals(2, invalidRepositories.size)
  }

  @Test
  fun `test keeps existing valid repo`() {
    packageService.addRepository("https://existing.example.com/simple/")
    repositories.add(PyPackageRepository("existing", "https://existing.example.com/simple/", null))

    syncRepositories(repositories, invalidRepositories, packageService)

    assertEquals(1, packageService.additionalRepositories.size)
  }
}
