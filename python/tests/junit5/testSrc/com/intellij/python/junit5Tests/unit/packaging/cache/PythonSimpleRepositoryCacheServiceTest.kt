// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging.cache

import com.intellij.idea.TestFor
import com.intellij.openapi.components.service
import com.intellij.python.junit5Tests.framework.pypi.MockPyPIServer
import com.intellij.python.junit5Tests.framework.pypi.mockPyPIServerFixture
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.PyPackage
import com.jetbrains.python.packaging.cache.PythonSimpleRepositoryCacheService
import com.jetbrains.python.packaging.repository.PyPackageRepositories
import com.jetbrains.python.packaging.repository.PyPackageRepository
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
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
    synchronized(repoService.repositories) {
      repoService.repositories.add(repo)
    }
    initialized(repo) {
      synchronized(repoService.repositories) {
        repoService.repositories.remove(repo)
      }
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
    assertTrue(cache.contains("numpy"), "numpy should appear in cache after reloadAll")
    assertTrue(cache.contains("pandas"), "pandas should appear in cache after reloadAll")
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
    assertTrue(firstCache.contains("numpy"), "numpy should be in first repo cache")
    assertTrue(firstCache.contains("pandas" ), "pandas should be in first repo cache")
    assertFalse(firstCache.contains("requests"), "requests should not leak into first repo cache")

    val secondCache = checkNotNull(cacheService[secondRepo]) { "Second repo cache should be populated" }
    assertTrue(secondCache.contains("requests"), "requests should be in second repo cache")
    assertTrue(secondCache.contains("urllib3" ), "urllib3 should be in second repo cache")
    assertFalse(secondCache.contains("numpy"), "numpy should not leak into second repo cache")
  }

  @Test
  @TestFor(issues = ["PY-92435"])
  fun `reloadAll keeps the old cache and logs no error when a repository is offline`(): Unit = runBlocking {
    val repo = repoFixture.get()
    val cacheService = service<PythonSimpleRepositoryCacheService>()
    assertThat(cacheService.reloadAll()).isInstanceOf(Result.Success::class.java)

    serverFixture.get().stop()
    val refreshErrors = mutableListOf<String>()
    val errorRecorder = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        if (message.startsWith("Failed to refresh repository")) {
          refreshErrors += message
          return Action.NONE
        }
        return super.processError(category, message, details, t)
      }
    }
    val result = LoggedErrorProcessor.executeWith(errorRecorder).use { cacheService.reloadAll() }

    assertThat(refreshErrors).isEmpty()
    assertThat(result).isInstanceOf(Result.Failure::class.java)
    assertThat(cacheService[repo]?.contains("numpy")).isTrue()
    assertThat(service<PyPackageRepositories>().invalidRepositories).contains(repo)
  }

  @Test
  @TestFor(issues = ["PY-92435"])
  fun `reloadAll skips a disabled repository`(): Unit = runBlocking {
    val repo = repoFixture.get()
    val missingUrl = "${serverFixture.get().simpleUrl}missing/"
    val disabledRepo = PyPackageRepository("disabled-repo", missingUrl, null)
    disabledRepo.enabled = false
    val repoService = service<PyPackageRepositories>()
    repoService.repositories.add(disabledRepo)
    try {
      val cacheService = service<PythonSimpleRepositoryCacheService>()

      assertThat(cacheService.reloadAll()).isInstanceOf(Result.Success::class.java)
      assertThat(cacheService[repo]?.contains("numpy")).isTrue()
      assertThat(cacheService[disabledRepo]?.isEmpty()).isTrue()
      assertThat(repoService.invalidRepositories).doesNotContain(disabledRepo)
    }
    finally {
      repoService.repositories.remove(disabledRepo)
    }
  }
}
