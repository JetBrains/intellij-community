package com.jetbrains.python.packaging.pip

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.io.delete
import com.intellij.util.io.write
import com.jetbrains.python.Result
import com.jetbrains.python.fixtures.PyTestCase
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
    val cache = PypiPackageCache()
    runBlocking { cache.reloadCache().orThrow() }
    assertThat(cache.packages).contains("c-pkg", "a-pkg", "b-pkg")
  }

  fun testCacheShouldBeUpdatedIfLocalStorageIsExpired() {
    withLocalStoredPackages(listOf("a-pkg"), Instant.now().minus(Duration.ofDays(2)))
    withPypiPackages(listOf("c-pkg", "b-pkg", "a-pkg"))
    val cache = PypiPackageCache()
    runBlocking { cache.reloadCache().orThrow() }
    assertThat(cache.packages).contains("c-pkg", "a-pkg", "b-pkg")
  }

  fun testBrokenLocalStorageShouldBeGracefullyHandled() {
    withBrokenLocalStorage()
    withPypiPackages(listOf("c-pkg", "b-pkg", "a-pkg"))
    val cache = PypiPackageCache()
    runBlocking { cache.reloadCache().orThrow() }
    assertThat(cache.packages).contains("c-pkg", "a-pkg", "b-pkg")
  }

  private fun withEmptyCacheStorage() {
    service<PypiPackageCache>().filePath.delete()
  }

  private fun withLocalStoredPackages(pypiPackages: List<String>, modifiedAt: Instant) {
    val filePath = service<PypiPackageCache>().filePath
    filePath.write(Gson().toJson(pypiPackages))
    filePath.setLastModifiedTime(FileTime.from(modifiedAt))
  }

  private fun withBrokenLocalStorage() {
    val filePath = service<PypiPackageCache>().filePath
    filePath.write("corrupted")
    filePath.setLastModifiedTime(FileTime.from(Instant.now()))
  }

  private fun withPypiPackages(pypiPackages: List<String>) {
    val mock = Mockito.mock(PypiPackageCache.PypiPackageLoader::class.java)
    Mockito.`when`(mock.loadPackages()).thenReturn(Result.success(pypiPackages))
    ApplicationManager.getApplication().registerServiceInstance(
      PypiPackageCache.PypiPackageLoader::class.java,
      mock
    )
  }

  private fun withPypiLoaderThrowingError() {
    val mock = Mockito.mock(PypiPackageCache.PypiPackageLoader::class.java)
    Mockito.`when`(mock.loadPackages()).thenAnswer { error("Should not be invoked") }
    ApplicationManager.getApplication().registerServiceInstance(
      PypiPackageCache.PypiPackageLoader::class.java,
      mock
    )
  }
}