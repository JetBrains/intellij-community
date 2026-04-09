// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.repository.search.completion

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionContributor
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.repository.search.completion.impl.DependencyCompletionServiceImpl
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private val GRADLE_SYSTEM_ID = ProjectSystemId("Gradle")
private val MAVEN_SYSTEM_ID = ProjectSystemId("Maven")

private object TestEelDescriptor : EelDescriptor {
  override val name: String get() = "test"
  override val osFamily: EelOsFamily get() = EelOsFamily.Posix
}

private class TestContext(override val buildSystemId: ProjectSystemId = GRADLE_SYSTEM_ID) : DependencyCompletionContext {
  override val eelDescriptor: EelDescriptor get() = TestEelDescriptor
}

private fun testResult(groupId: String = "g", artifactId: String = "a", version: String = "1.0") =
  DependencyCompletionResult(groupId, artifactId, version, source = DependencyCompletionContributionSource.SERVER)

private fun testPartResult(value: String) =
  DependencyPartCompletionResult(value, source = DependencyCompletionContributionSource.SERVER)

private fun fakeContributor(
  systemId: ProjectSystemId = GRADLE_SYSTEM_ID,
  enabled: Boolean = true,
  searchResults: List<DependencyCompletionResult> = emptyList(),
  groupResults: List<String> = emptyList(),
  failWith: Exception? = null,
): DependencyCompletionContributor = object : DependencyCompletionContributor {
  override val buildSystemId: ProjectSystemId get() = systemId
  override fun isEnabled(): Boolean = enabled

  override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
    if (failWith != null) throw failWith
    return searchResults
  }

  override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<DependencyPartCompletionResult> {
    if (failWith != null) throw failWith
    return groupResults.map { testPartResult(it) }
  }

  override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<DependencyPartCompletionResult> {
    if (failWith != null) throw failWith
    return emptyList()
  }

  override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<DependencyPartCompletionResult> {
    if (failWith != null) throw failWith
    return emptyList()
  }
}

@TestApplication
class DependencyCompletionServiceImplTest {

  @TestDisposable
  private lateinit var disposable: Disposable

  private val gradleContext = TestContext(GRADLE_SYSTEM_ID)
  private val searchRequest = DependencyCompletionRequest("junit", gradleContext)
  private val groupRequest = DependencyGroupCompletionRequest("org", "", gradleContext)

  private fun withContributors(vararg contributors: DependencyCompletionContributor): DependencyCompletionServiceImpl {
    ExtensionTestUtil.maskExtensions(DependencyCompletionService.EP_NAME, contributors.toList(), disposable)
    return DependencyCompletionServiceImpl()
  }

  @Test
  fun `disabled contributor is excluded from search`() = runBlocking {
    val service = withContributors(
      fakeContributor(enabled = false, searchResults = listOf(testResult()))
    )
    val results = service.suggestCompletions(searchRequest).toList()
    assertThat(results).isEmpty()
  }

  @Test
  fun `contributor with mismatched buildSystemId is excluded`() = runBlocking {
    val service = withContributors(
      fakeContributor(systemId = MAVEN_SYSTEM_ID, searchResults = listOf(testResult()))
    )
    val results = service.suggestCompletions(searchRequest).toList()
    assertThat(results).isEmpty()
  }

  @Test
  fun `results from multiple matching contributors are combined`(): Unit = runBlocking {
    val result1 = testResult("com.example", "lib", "1.0")
    val result2 = testResult("org.junit", "junit", "4.13")
    val service = withContributors(
      fakeContributor(searchResults = listOf(result1)),
      fakeContributor(searchResults = listOf(result2)),
    )
    val results = service.suggestCompletions(searchRequest).toList()
    assertThat(results).containsExactlyInAnyOrder(result1, result2)
  }

  @Test
  fun `failing contributor does not stop other contributors`(): Unit = runBlocking {
    val goodResult = testResult("com.example", "lib", "1.0")
    val service = withContributors(
      fakeContributor(failWith = RuntimeException("boom")),
      fakeContributor(searchResults = listOf(goodResult)),
    )
    val results = service.suggestCompletions(searchRequest).toList()
    assertThat(results).containsExactly(goodResult)
  }

  @Test
  fun `suggestCompletions returns empty when no contributors are registered`() = runBlocking {
    val service = withContributors()
    val results = service.suggestCompletions(searchRequest).toList()
    assertThat(results).isEmpty()
  }

  @Test
  fun `suggestGroupCompletions applies distinctUntilChanged deduplication`() = runBlocking {
    // Two contributors return the same groups; with distinctUntilChanged the flow removes consecutive dupes
    val service = withContributors(
      fakeContributor(groupResults = listOf("com.example", "org.junit")),
      fakeContributor(groupResults = listOf("com.example", "org.junit")),
    )
    val results = service.suggestGroupCompletions(groupRequest).toList()
    // Result should have no consecutive duplicates (distinctUntilChanged guarantee)
    val resultStrings = results.map { it.result }
    for (i in 0 until resultStrings.size - 1) {
      assertThat(resultStrings[i]).isNotEqualTo(resultStrings[i + 1])
    }
  }

  @Test
  fun `enabled contributor matching buildSystemId receives the request`() = runBlocking {
    var receivedRequest: DependencyCompletionRequest? = null
    val capturingContributor = object : DependencyCompletionContributor {
      override val buildSystemId: ProjectSystemId get() = GRADLE_SYSTEM_ID
      override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
        receivedRequest = request
        return emptyList()
      }

      override suspend fun getGroups(request: DependencyGroupCompletionRequest) = emptyList<DependencyPartCompletionResult>()
      override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest) = emptyList<DependencyPartCompletionResult>()
      override suspend fun getVersions(request: DependencyVersionCompletionRequest) = emptyList<DependencyPartCompletionResult>()
    }
    ExtensionTestUtil.maskExtensions(DependencyCompletionService.EP_NAME, listOf(capturingContributor), disposable)
    DependencyCompletionServiceImpl().suggestCompletions(searchRequest).toList()
    assertEquals(searchRequest, receivedRequest)
  }
}
