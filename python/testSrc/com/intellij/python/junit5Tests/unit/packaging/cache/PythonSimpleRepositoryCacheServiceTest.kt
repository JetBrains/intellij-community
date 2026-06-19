// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.cache

import com.intellij.openapi.components.service
import com.intellij.python.junit5Tests.framework.pypi.MockPyPIServer
import com.intellij.python.junit5Tests.framework.pypi.mockPyPIServerFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCacheService
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPackageRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class PythonSimpleRepositoryCacheServiceTest {

  private val serverFixture = mockPyPIServerFixture(
    PyPackage("numpy", "1.0.0"),
    PyPackage("pandas", "2.0.0"),
  )
  private val repoFixture = pyPackageRepositoryFixture(serverFixture)

  private val secondServerFixture = mockPyPIServerFixture(
    PyPackage("requests", "2.31.0"),
    PyPackage("urllib3", "2.0.0"),
  )
  private val secondRepoFixture = pyPackageRepositoryFixture(secondServerFixture, name = "second-repo")

  @AfterEach
  fun tearDown() {
    service<PyPackageRepositories>().invalidRepositories.clear()
  }

  private fun pyPackageRepositoryFixture(
    server: TestFixture<MockPyPIServer>,
    name: String = "test-repo",
  ): TestFixture<PyPackageRepository> = testFixture {
    val srv = server.init()
    val repo = PyPackageRepository(name, srv.simpleUrl, srv.credentials?.login)
    srv.credentials?.let { repo.setPassword(it.password) }
    val repoService = service<PyPackageRepositories>()
    repoService.repositories.add(repo)
    initialized(repo) {
      repoService.repositories.remove(repo)
      if (srv.credentials != null) {
        repo.clearCredentials()
      }
    }
  }

  @Test
  fun `reloadAll populates cache with packages from repository`() = runBlocking {
    val repo = repoFixture.get()
    val cacheService = service<PythonSimpleRepositoryCacheService>()

    assertNull(cacheService[repo], "Cache should be empty before reloadAll")

    val result = cacheService.reloadAll()
    assertTrue(result is Result.Success, "reloadAll should succeed: $result")

    val cache = checkNotNull(cacheService[repo]) { "Cache should be populated for added repository" }
    assertTrue("numpy" in cache, "numpy should appear in cache after reloadAll")
    assertTrue("pandas" in cache, "pandas should appear in cache after reloadAll")
  }

  @Test
  fun `reloadAll populates each repository cache independently`() = runBlocking {
    val firstRepo = repoFixture.get()
    val secondRepo = secondRepoFixture.get()
    val cacheService = service<PythonSimpleRepositoryCacheService>()

    assertNull(cacheService[firstRepo], "First repo cache should be empty before reloadAll")
    assertNull(cacheService[secondRepo], "Second repo cache should be empty before reloadAll")

    val result = cacheService.reloadAll()
    assertTrue(result is Result.Success, "reloadAll should succeed: $result")

    val firstCache = checkNotNull(cacheService[firstRepo]) { "First repo cache should be populated" }
    assertTrue("numpy" in firstCache, "numpy should be in first repo cache")
    assertTrue("pandas" in firstCache, "pandas should be in first repo cache")
    assertTrue("requests" !in firstCache, "requests should not leak into first repo cache")

    val secondCache = checkNotNull(cacheService[secondRepo]) { "Second repo cache should be populated" }
    assertTrue("requests" in secondCache, "requests should be in second repo cache")
    assertTrue("urllib3" in secondCache, "urllib3 should be in second repo cache")
    assertTrue("numpy" !in secondCache, "numpy should not leak into second repo cache")
  }
}
