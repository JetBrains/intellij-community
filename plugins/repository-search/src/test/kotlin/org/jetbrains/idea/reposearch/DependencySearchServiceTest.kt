package org.jetbrains.idea.reposearch

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.WaitFor
import junit.framework.TestCase
import org.jetbrains.concurrency.isPending
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class DependencySearchServiceTest : LightPlatformTestCase() {

  private lateinit var dependencySearchService: DependencySearchService
  override fun setUp() {
    super.setUp()
    dependencySearchService = DependencySearchService(project)
    Disposer.register(testRootDisposable, dependencySearchService)
  }


  fun testShouldReturnDataFromCache() {


    var requests = 0

    val testProvider = object : TestSearchProvider() {
      override fun isLocal() = false

      override fun fulltextSearch(searchString: String): CompletableFuture<List<RepositoryArtifactData>> = CompletableFuture.supplyAsync {
        requests++
        listOf(RepositoryArtifactData { searchString })
      }
    }
    ExtensionTestUtil.maskExtensions(DependencySearchService.EP_NAME, listOf(DependencySearchProvidersFactory { listOf(testProvider) }), testRootDisposable, false)
    val searchParameters = SearchParameters(true, false)

    val promise = dependencySearchService.fulltextSearch("something", searchParameters) {}
    object : WaitFor(500) {
      override fun condition() = !promise.isPending
    }
    assertTrue(promise.isSucceeded)
    TestCase.assertEquals(1, requests)
    dependencySearchService.fulltextSearch("something", searchParameters) {}
    TestCase.assertEquals(1, requests)
    val promise1 = dependencySearchService.fulltextSearch("something", SearchParameters(false, false)) {}
    object : WaitFor(500) {
      override fun condition() = !promise1.isPending
    }
    assertTrue(promise1.isSucceeded)
    TestCase.assertEquals(2, requests)
  }


  open class TestSearchProvider : DependencySearchProvider {

    override fun fulltextSearch(searchString: String): CompletableFuture<List<RepositoryArtifactData>> {
      TODO("Not yet implemented")
    }

    override fun suggestPrefix(groupId: String?, artifactId: String?): CompletableFuture<List<RepositoryArtifactData>> {
      TODO("Not yet implemented")
    }

    override fun isLocal(): Boolean {
      TODO("Not yet implemented")
    }

  }

}