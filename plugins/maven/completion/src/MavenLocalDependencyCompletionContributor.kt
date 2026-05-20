// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion

import com.intellij.maven.completion.provider.IndexBasedCompletionProvider
import com.intellij.maven.completion.provider.ProjectModulesCompletionProvider
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionContributor
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path

@ApiStatus.Internal
internal class MavenLocalDependencyCompletionContributor : DependencyCompletionContributor {
  override val source: DependencyCompletionContributionSource = DependencyCompletionContributionSource.LOCAL

  override val buildSystemId: ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun isEnabled(): Boolean {
    return Registry.`is`("maven.dependency.completion.contributor.local")
  }

  // TODO: pass project as an optional parameter (?)
  private fun findProject(eelDescriptor: EelDescriptor): Project? =
    ProjectManager.getInstance().openProjects.firstOrNull { project ->
      project.basePath?.let { Path.of(it).getEelDescriptor() } == eelDescriptor
    }

  override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
    val project = findProject(request.context.eelDescriptor) ?: return emptyList()
    val searchString = request.searchString
    val providers = listOf(IndexBasedCompletionProvider(project), ProjectModulesCompletionProvider(project))
    return providers.flatMap { provider ->
      val artifacts = if (searchString.contains(":")) {
        val parts = searchString.split(":", limit = 2)
        provider.suggestPrefix(parts[0], parts.getOrElse(1) { "" })
      }
      else {
        provider.fulltextSearch(searchString)
      }
      artifacts.flatMap { info ->
        info.items.map { item ->
          DependencyCompletionResult(info.groupId, info.artifactId, item.version ?: "", source = source)
        }
      }
    }
  }

  override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<DependencyPartCompletionResult> {
    val project = findProject(request.context.eelDescriptor) ?: return emptyList()
    val prefix = request.groupPrefix
    val index = MavenIndicesManager.getInstance(project).getCommonGavIndex()
    return index.groupIds
      .filter { prefix.isEmpty() || it.contains(prefix, ignoreCase = true) }
      .map { DependencyPartCompletionResult(it, source) }
  }

  override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<DependencyPartCompletionResult> {
    val project = findProject(request.context.eelDescriptor) ?: return emptyList()
    val prefix = request.artifactPrefix
    val index = MavenIndicesManager.getInstance(project).getCommonGavIndex()
    return index.getArtifactIds(request.group)
      .filter { prefix.isEmpty() || it.contains(prefix, ignoreCase = true) }
      .map { DependencyPartCompletionResult(it, source) }
  }

  override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<DependencyPartCompletionResult> {
    val project = findProject(request.context.eelDescriptor) ?: return emptyList()
    val prefix = request.versionPrefix
    val index = MavenIndicesManager.getInstance(project).getCommonGavIndex()
    return index.getVersions(request.group, request.artifact)
      .filter { prefix.isEmpty() || it.contains(prefix, ignoreCase = true) }
      .map { DependencyPartCompletionResult(it, source) }
  }
}
