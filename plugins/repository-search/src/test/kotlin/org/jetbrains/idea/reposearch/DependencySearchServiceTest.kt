package org.jetbrains.idea.reposearch

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.WaitFor
import junit.framework.TestCase
import org.jetbrains.concurrency.isPending
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedDeque

class DependencySearchServiceTest : LightPlatformTestCase() {

  private lateinit var dependencySearchService: DependencySearchService
  override fun setUp() {
    super.setUp()
    dependencySearchService = DependencySearchService(project)
    Disposer.register(testRootDisposable, dependencySearchService)
  }


  fun testShouldMergeVersionsFromDifferentProviders() {

    val testProviderLocal1 = object : TestSearchProvider() {
      override fun isLocal() = true

      override fun suggestPrefix(groupId: String, artifactId: String): CompletableFuture<List<RepositoryArtifactData>> {
        return CompletableFuture.supplyAsync {
          listOf(MavenRepositoryArtifactInfo(groupId, artifactId, listOf("0", "1")))
        }
      }
    }

    val testProviderLocal2 = object : TestSearchProvider() {
      override fun isLocal() = true

      override fun suggestPrefix(groupId: String, artifactId: String): CompletableFuture<List<RepositoryArtifactData>> {
        return CompletableFuture.supplyAsync {
          listOf(MavenRepositoryArtifactInfo(groupId, artifactId, listOf("2")))
        }
      }
    }

    val testProviderRemote3 = object : TestSearchProvider() {
      override fun isLocal() = false

      override fun suggestPrefix(groupId: String, artifactId: String): CompletableFuture<List<RepositoryArtifactData>> {
        return CompletableFuture.supplyAsync {
          listOf(MavenRepositoryArtifactInfo(groupId, artifactId, listOf("3")))
        }
      }
    }

    val testProviderRemote4 = object : TestSearchProvider() {
      override fun isLocal() = false

      override fun suggestPrefix(groupId: String, artifactId: String): CompletableFuture<List<RepositoryArtifactData>> {
        return CompletableFuture.supplyAsync {
          listOf(MavenRepositoryArtifactInfo(groupId, artifactId, listOf("4")))
        }
      }
    }

    ExtensionTestUtil.maskExtensions(DependencySearchService.EP_NAME, listOf(DependencySearchProvidersFactory {
      listOf(
        testProviderLocal1, testProviderLocal2, testProviderRemote3, testProviderRemote4)
    }), testRootDisposable, false)
    val searchParameters = SearchParameters(false, false)

    val result = ConcurrentLinkedDeque<RepositoryArtifactData>()
    val promise = dependencySearchService.suggestPrefix("group", "artifact", searchParameters) {
      result.add(it)
    }
    object : WaitFor(2_000) {
      override fun condition(): Boolean {
        return result.last() === PoisonedRepositoryArtifactData.INSTANCE
      }
    }
    TestCase.assertTrue(result.last() === PoisonedRepositoryArtifactData.INSTANCE)
    val artifactResults = result.filterIsInstance(MavenRepositoryArtifactInfo::class.java)
    UsefulTestCase.assertSameElements(artifactResults.flatMap { it.items.asList() }.map { it.version },
                                      "0", "1", "2", "3", "4")
  }

  fun testShouldReturnDataFromCache() {


    var requests = 0

    val testProvider = object : TestSearchProvider() {
      override fun isLocal() = false

      override fun fulltextSearch(searchString: String): CompletableFuture<List<RepositoryArtifactData>> = CompletableFuture.supplyAsync {
        requests++
        listOf(object : RepositoryArtifactData {
          override fun getKey() = searchString

          override fun mergeWith(another: RepositoryArtifactData) = this
        })
      }
    }
    ExtensionTestUtil.maskExtensions(DependencySearchService.EP_NAME, listOf(DependencySearchProvidersFactory { listOf(testProvider) }),
                                     testRootDisposable, false)
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

    override fun suggestPrefix(groupId: String, artifactId: String): CompletableFuture<List<RepositoryArtifactData>> {
      TODO("Not yet implemented")
    }

    override fun isLocal(): Boolean {
      TODO("Not yet implemented")
    }

  }

}