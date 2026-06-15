package com.jetbrains.python.packaging.pip

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.io.write
import com.jetbrains.python.Result
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.getOrThrow
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import kotlin.io.path.setLastModifiedTime

class PypiPackageCacheTest : PyTestCase() {
  fun testCacheShouldNotBeUpdatedIfLocalStorageIsntExpired() {
    withLocalStoredPackages(listOf("c-pkg", "a-pkg", "b-pkg"), Instant.now())
    withPypiLoaderThrowingError()
    val cache = PyPiPackageCache()
    runBlocking { cache.reloadCache().orThrow() }
    assertThat(cache.search("").pages[0].contents().getOrThrow().toList()).contains("c-pkg", "a-pkg", "b-pkg")
  }

  fun testCacheShouldBeUpdatedIfLocalStorageIsExpired() {
    withLocalStoredPackages(listOf("a-pkg"), Instant.now().minus(Duration.ofDays(2)))
    withPypiPackages(listOf("c-pkg", "b-pkg", "a-pkg"))
    val cache = PyPiPackageCache()
    runBlocking { cache.reloadCache().orThrow() }
    assertThat(cache.search("").pages[0].contents().getOrThrow().toList()).contains("c-pkg", "a-pkg", "b-pkg")
  }

  fun testBrokenLocalStorageShouldBeGracefullyHandled() {
    withBrokenLocalStorage()
    withPypiPackages(listOf("c-pkg", "b-pkg", "a-pkg"))
    val cache = PyPiPackageCache()
    runBlocking { cache.reloadCache().orThrow() }
    assertThat(cache.search("").pages[0].contents().getOrThrow().toList()).contains("c-pkg", "a-pkg", "b-pkg")
  }

  private fun withLocalStoredPackages(pypiPackages: List<String>, modifiedAt: Instant) {
    val filePath = service<PyPiPackageCache>().filePath
    filePath.write(
      buildString {
        for (pkg in pypiPackages.sorted()) {
          append(pkg)
          append('\n')
        }
      }
    )
    filePath.setLastModifiedTime(FileTime.from(modifiedAt))
  }

  private fun withBrokenLocalStorage() {
    val filePath = service<PyPiPackageCache>().filePath
    filePath.write("corrupted")
    filePath.setLastModifiedTime(FileTime.from(Instant.now()))
  }

  private fun withPypiPackages(pypiPackages: List<String>) {
    val mock = Mockito.mock(PyPiPackageCache.PyPiPackageLoader::class.java)
    Mockito.`when`(mock.loadPackages()).thenReturn(Result.success(pypiPackages.sorted()))
    ApplicationManager.getApplication().registerServiceInstance(
      PyPiPackageCache.PyPiPackageLoader::class.java,
      mock
    )
  }

  private fun withPypiLoaderThrowingError() {
    val mock = Mockito.mock(PyPiPackageCache.PyPiPackageLoader::class.java)
    Mockito.`when`(mock.loadPackages()).thenAnswer { error("Should not be invoked") }
    ApplicationManager.getApplication().registerServiceInstance(
      PyPiPackageCache.PyPiPackageLoader::class.java,
      mock
    )
  }
}