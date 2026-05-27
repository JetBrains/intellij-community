// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.repository.search.completion

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionContributor
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
import kotlin.time.Duration.Companion.milliseconds

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
  fun `suggestGroupCompletions deduplicates results from LOCAL and SERVER contributors`(): Unit = runBlocking {
    val service = withContributors(
      fakeContributor(contributorSource = DependencyCompletionContributionSource.SERVER,
                      groupResults = listOf("org.springframework", "com.google")),
      fakeContributor(contributorSource = DependencyCompletionContributionSource.LOCAL,
                      groupResults = listOf("org.springframework", "org.apache")),
    )
    val results = service.suggestGroupCompletions(groupRequest).toList().map { it.result }
    assertThat(results).containsExactlyInAnyOrder("org.springframework", "com.google", "org.apache")
  }

  @Test
  fun `suggestArtifactCompletions deduplicates results from LOCAL and SERVER contributors`(): Unit = runBlocking {
    val artifactRequest = DependencyArtifactCompletionRequest("org.springframework", "spring-b", gradleContext)
    val service = withContributors(
      fakeContributor(contributorSource = DependencyCompletionContributionSource.SERVER,
                      artifactResults = listOf("spring-beans", "spring-boot")),
      fakeContributor(contributorSource = DependencyCompletionContributionSource.LOCAL,
                      artifactResults = listOf("spring-beans", "spring-context")),
    )
    val results = service.suggestArtifactCompletions(artifactRequest).toList().map { it.result }
    assertThat(results).containsExactlyInAnyOrder("spring-beans", "spring-boot", "spring-context")
  }

  @Test
  fun `suggestVersionCompletions deduplicates results from LOCAL and SERVER contributors`(): Unit = runBlocking {
    val versionRequest = DependencyVersionCompletionRequest("org.springframework", "spring-beans", "6.", gradleContext)
    val service = withContributors(
      fakeContributor(contributorSource = DependencyCompletionContributionSource.SERVER,
                      versionResults = listOf("6.1.0", "6.0.0")),
      fakeContributor(contributorSource = DependencyCompletionContributionSource.LOCAL,
                      versionResults = listOf("6.1.0", "5.3.0")),
    )
    val results = service.suggestVersionCompletions(versionRequest).toList().map { it.result }
    assertThat(results).containsExactlyInAnyOrder("6.1.0", "6.0.0", "5.3.0")
  }

  @Test
  fun `suggestCompletions deduplicates results from LOCAL and SERVER contributors`(): Unit = runBlocking {
    val serverResult = DependencyCompletionResult("org.springframework", "spring-beans", "6.1.0",
                                                  source = DependencyCompletionContributionSource.SERVER)
    val localDuplicate = DependencyCompletionResult("org.springframework", "spring-beans", "6.1.0",
                                                    source = DependencyCompletionContributionSource.LOCAL)
    val localUnique = DependencyCompletionResult("org.springframework", "spring-beans", "5.3.0",
                                                 source = DependencyCompletionContributionSource.LOCAL)
    val service = withContributors(
      fakeContributor(contributorSource = DependencyCompletionContributionSource.SERVER,
                      searchResults = listOf(serverResult)),
      fakeContributor(contributorSource = DependencyCompletionContributionSource.LOCAL,
                      searchResults = listOf(localDuplicate, localUnique)),
    )
    val results = service.suggestCompletions(searchRequest).toList()
    assertThat(results).containsExactlyInAnyOrder(serverResult, localUnique)
  }

  @Test
  fun `enabled contributor matching buildSystemId receives the request`() = runBlocking {
    var receivedRequest: DependencyCompletionRequest? = null
    val capturingContributor = object : DependencyCompletionContributor {
      override val buildSystemId: ProjectSystemId get() = GRADLE_SYSTEM_ID
      override val source: DependencyCompletionContributionSource = DependencyCompletionContributionSource.LOCAL

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

  @Test
  fun `local results arrive after server results even when local contributor is faster`(): Unit = runBlocking {
    val serverResult = testResult("server-group", "artifact", "2.0")
    val localResult = DependencyCompletionResult("local-group", "artifact", "1.0", source = DependencyCompletionContributionSource.LOCAL)
    val service = withContributors(
      fakeContributor(contributorSource = DependencyCompletionContributionSource.SERVER,
                      delayMs = 50L,
                      searchResults = listOf(serverResult)),
      fakeContributor(contributorSource = DependencyCompletionContributionSource.LOCAL, searchResults = listOf(localResult)),
    )
    val results = service.suggestCompletions(searchRequest).toList()
    assertThat(results).hasSize(2)
    assertThat(results[0]).isEqualTo(serverResult)
    assertThat(results[1]).isEqualTo(localResult)
  }
}

private val GRADLE_SYSTEM_ID = ProjectSystemId("Gradle")
private val MAVEN_SYSTEM_ID = ProjectSystemId("Maven")

private class TestContext(override val buildSystemId: ProjectSystemId = GRADLE_SYSTEM_ID) : DependencyCompletionContext {
  override val project: Project get() = ProjectManager.getInstance().defaultProject
}

private fun testResult(groupId: String = "g", artifactId: String = "a", version: String = "1.0") =
  DependencyCompletionResult(groupId, artifactId, version, source = DependencyCompletionContributionSource.SERVER)

private fun testPartResult(value: String) =
  DependencyPartCompletionResult(value, source = DependencyCompletionContributionSource.SERVER)

private fun fakeContributor(
  systemId: ProjectSystemId = GRADLE_SYSTEM_ID,
  enabled: Boolean = true,
  contributorSource: DependencyCompletionContributionSource = DependencyCompletionContributionSource.LOCAL,
  delayMs: Long = 0L,
  searchResults: List<DependencyCompletionResult> = emptyList(),
  groupResults: List<String> = emptyList(),
  artifactResults: List<String> = emptyList(),
  versionResults: List<String> = emptyList(),
  failWith: Exception? = null,
): DependencyCompletionContributor = object : DependencyCompletionContributor {
  override val buildSystemId: ProjectSystemId get() = systemId
  override val source: DependencyCompletionContributionSource = contributorSource
  override fun isEnabled(): Boolean = enabled

  override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
    if (delayMs > 0) kotlinx.coroutines.delay(delayMs.milliseconds)
    if (failWith != null) throw failWith
    return searchResults
  }

  override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<DependencyPartCompletionResult> {
    if (failWith != null) throw failWith
    return groupResults.map { testPartResult(it) }
  }

  override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<DependencyPartCompletionResult> {
    if (failWith != null) throw failWith
    return artifactResults.map { testPartResult(it) }
  }

  override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<DependencyPartCompletionResult> {
    if (failWith != null) throw failWith
    return versionResults.map { testPartResult(it) }
  }
}