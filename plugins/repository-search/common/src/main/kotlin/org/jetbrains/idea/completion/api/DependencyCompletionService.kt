// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental

package org.jetbrains.idea.completion.api

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.ApiStatus

interface DependencyCompletionService {
  fun suggestCompletions(request: DependencyCompletionRequest): Flow<DependencyCompletionResult> = flowOf()
  fun suggestGroupCompletions(request: DependencyGroupCompletionRequest): Flow<String> = flowOf()
  fun suggestArtifactCompletions(request: DependencyArtifactCompletionRequest): Flow<String> = flowOf()
  fun suggestVersionCompletions(request: DependencyVersionCompletionRequest): Flow<String> = flowOf()

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<DependencyCompletionContributor> = ExtensionPointName("org.jetbrains.idea.reposearch.contributor")
  }
}

interface DependencyCompletionContributor {
  fun isApplicable(context: DependencyCompletionContext): Boolean
  suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult>
  suspend fun getGroups(request: DependencyGroupCompletionRequest): List<String>
  suspend fun getArtifacts(request: DependencyArtifactCompletionRequest) : List<String>
  suspend fun getVersions(request: DependencyVersionCompletionRequest) : List<String>
}

interface DependencyCompletionContext {
  val eelDescriptor: EelDescriptor
}

class GradleDependencyCompletionContext(override val eelDescriptor: EelDescriptor) : DependencyCompletionContext

class MavenDependencyCompletionContext(override val eelDescriptor: EelDescriptor) : DependencyCompletionContext

class MavenPluginDependencyCompletionContext(override val eelDescriptor: EelDescriptor) : DependencyCompletionContext

class MavenExtensionDependencyCompletionContext(override val eelDescriptor: EelDescriptor) : DependencyCompletionContext

interface BaseDependencyCompletionRequest {
  val context: DependencyCompletionContext
}

data class DependencyCompletionRequest(val searchString: String, override val context: DependencyCompletionContext) : BaseDependencyCompletionRequest

data class DependencyGroupCompletionRequest(val groupPrefix: String, val artifact: String, override val context: DependencyCompletionContext) : BaseDependencyCompletionRequest

data class DependencyArtifactCompletionRequest(val group: String, val artifactPrefix: String, override val context: DependencyCompletionContext) : BaseDependencyCompletionRequest

data class DependencyVersionCompletionRequest(val group: String, val artifact: String, val versionPrefix: String, override val context: DependencyCompletionContext) : BaseDependencyCompletionRequest


data class DependencyCompletionResult(val groupId: String, val artifactId: String, val version: String, val scope: String? = null)