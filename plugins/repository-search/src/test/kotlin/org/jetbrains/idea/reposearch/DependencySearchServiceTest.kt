package org.jetbrains.idea.reposearch

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

class DependencySearchServiceTest : LightPlatformTestCase() {
  override fun runInDispatchThread() = false

  private lateinit var dependencySearchService: DependencySearchService
  override fun setUp() {
    super.setUp()
    dependencySearchService = project.service<DependencySearchService>()
    Disposer.register(testRootDisposable, dependencySearchService)
  }


  fun testShouldMergeVersionsFromDifferentProviders() = runBlocking {

    val testProviderLocal1 = object : TestSearchProvider() {
      override fun isLocal() = true

      override suspend fun suggestPrefix(groupId: String, artifactId: String): List<RepositoryArtifactData> {
        return listOf(MavenRepositoryArtifactInfo(groupId, artifactId, listOf("0", "1")))
      }
    }

    val testProviderLocal2 = object : TestSearchProvider() {
      override fun isLocal() = true

      override suspend fun suggestPrefix(groupId: String, artifactId: String): List<RepositoryArtifactData> {
        return listOf(MavenRepositoryArtifactInfo(groupId, artifactId, listOf("2")))
      }
    }

    val testProviderRemote3 = object : TestSearchProvider() {
      override fun isLocal() = false

      override suspend fun suggestPrefix(groupId: String, artifactId: String): List<RepositoryArtifactData> {
        return listOf(MavenRepositoryArtifactInfo(groupId, artifactId, listOf("3")))
      }
    }

    val testProviderRemote4 = object : TestSearchProvider() {
      override fun isLocal() = false

      override suspend fun suggestPrefix(groupId: String, artifactId: String): List<RepositoryArtifactData> {
        return listOf(MavenRepositoryArtifactInfo(groupId, artifactId, listOf("4")))
      }
    }

    ExtensionTestUtil.maskExtensions(DependencySearchService.EP_NAME, listOf(DependencySearchProvidersFactory {
      listOf(
        testProviderLocal1, testProviderLocal2, testProviderRemote3, testProviderRemote4)
    }), testRootDisposable, false)
    val searchParameters = SearchParameters(false, false)

    val result = ConcurrentLinkedDeque<RepositoryArtifactData>()
    dependencySearchService.suggestPrefixAsync("group", "artifact", searchParameters) {
      result.add(it)
    }
    val artifactResults = result.filterIsInstance<MavenRepositoryArtifactInfo>()
    UsefulTestCase.assertSameElements(artifactResults.flatMap { it.items.asList() }.map { it.version },
                                      "0", "1", "2", "3", "4")
  }

  fun testShouldReturnDataFromCache() = runBlocking {
    var requests = 0

    val testProvider = object : TestSearchProvider() {
      override fun isLocal() = false

      override suspend fun fulltextSearch(searchString: String): List<RepositoryArtifactData> {
        requests++
        return listOf(object : RepositoryArtifactData {
          override fun getKey() = searchString

          override fun mergeWith(another: RepositoryArtifactData) = this
        })
      }
    }
    ExtensionTestUtil.maskExtensions(DependencySearchService.EP_NAME, listOf(DependencySearchProvidersFactory { listOf(testProvider) }),
                                     testRootDisposable, false)
    val searchParameters = SearchParameters(true, false)

    dependencySearchService.fulltextSearchAsync("something", searchParameters) {}
    TestCase.assertEquals(1, requests)
    dependencySearchService.fulltextSearchAsync("something", searchParameters) {}
    TestCase.assertEquals(1, requests)
    dependencySearchService.fulltextSearchAsync("something", SearchParameters(false, false)) {}
    TestCase.assertEquals(2, requests)
  }


  open class TestSearchProvider : DependencySearchProvider {

    private val _cacheKey = UUID.randomUUID().toString()

    override suspend fun fulltextSearch(searchString: String): List<RepositoryArtifactData> {
      TODO("Not yet implemented")
    }

    override suspend fun suggestPrefix(groupId: String, artifactId: String): List<RepositoryArtifactData> {
      TODO("Not yet implemented")
    }

    override fun isLocal(): Boolean {
      TODO("Not yet implemented")
    }

    override val cacheKey = _cacheKey

  }

}