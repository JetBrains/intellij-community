// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental

package com.intellij.repository.search.completion.api

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface DependencyCompletionService {
  fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionEvent<DependencyCompletionResult>> = flowOf()

  fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
    flowOf()

  fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
    flowOf()

  fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<DependencyCompletionEvent<DependencyPartCompletionResult>> =
    flowOf()

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<DependencyCompletionContributor> = ExtensionPointName("org.jetbrains.idea.reposearch.contributor")
  }
}

@ApiStatus.OverrideOnly
interface DependencyCompletionContributor {
  val buildSystemId: ProjectSystemId
  val source: DependencyCompletionContributionSource
  fun isEnabled(): Boolean = true
  suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult>
  suspend fun getGroups(request: DependencyGroupCompletionRequest): List<DependencyPartCompletionResult>
  suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<DependencyPartCompletionResult>
  suspend fun getVersions(request: DependencyVersionCompletionRequest): List<DependencyPartCompletionResult>
}

interface DependencyCompletionContext {
  val project: Project
  val buildSystemId: ProjectSystemId
}

class DependencyCompletionContextImpl(
  override val project: Project,
  override val buildSystemId: ProjectSystemId,
) : DependencyCompletionContext

interface BaseDependencyCompletionRequest {
  val context: DependencyCompletionContext
}

data class DependencyCompletionRequest(val searchString: String, override val context: DependencyCompletionContext) : BaseDependencyCompletionRequest

data class DependencyGroupCompletionRequest(val groupPrefix: String, val artifact: String, override val context: DependencyCompletionContext) : BaseDependencyCompletionRequest

data class DependencyArtifactCompletionRequest(val group: String, val artifactPrefix: String, override val context: DependencyCompletionContext) : BaseDependencyCompletionRequest

data class DependencyVersionCompletionRequest(val group: String, val artifact: String, val versionPrefix: String, override val context: DependencyCompletionContext) : BaseDependencyCompletionRequest


enum class DependencyCompletionContributionSource {
  LOCAL, SERVER
}

interface BaseDependencyCompletionResult {
  val source: DependencyCompletionContributionSource
}

data class DependencyCompletionResult(
  val groupId: String,
  val artifactId: String,
  val version: String,
  val scope: String? = null,
  override val source: DependencyCompletionContributionSource,
) : BaseDependencyCompletionResult

data class DependencyPartCompletionResult(val result: String, override val source: DependencyCompletionContributionSource) :
  BaseDependencyCompletionResult

/**
 * Stream item produced by [DependencyCompletionService] suggest* methods.
 *
 * In addition to actual [Item] results, the stream may emit terminal status events
 * for the SERVER side: [ServerTimedOut] when the underlying request exceeded its timeout,
 * and [ServerFailed] when the request failed for any other reason (e.g. server unreachable,
 * RPC error). LOCAL contributors do not emit status events.
 */
sealed interface DependencyCompletionEvent<out T : BaseDependencyCompletionResult> {
  data class Item<T : BaseDependencyCompletionResult>(val result: T) : DependencyCompletionEvent<T>
  data object ServerTimedOut : DependencyCompletionEvent<Nothing>
  data class ServerFailed(val cause: Throwable? = null) : DependencyCompletionEvent<Nothing>
}