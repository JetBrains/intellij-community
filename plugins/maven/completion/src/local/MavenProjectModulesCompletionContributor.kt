// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.local

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.util.registry.Registry
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionContributor
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import org.jetbrains.idea.maven.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil

internal class MavenProjectModulesCompletionContributor : DependencyCompletionContributor {
  override val source: DependencyCompletionContributionSource = DependencyCompletionContributionSource.LOCAL

  override val buildSystemId: ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun isEnabled(): Boolean {
    return Registry.`is`("maven.dependency.completion.contributor.local.modules")
  }

  override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
    val project = request.context.project
    val searchString = request.searchString
    MavenLog.LOG.debug("Project: get local maven artifacts started")
    val result = MavenProjectsManager.getInstance(project).projects
      .map { MavenDependencyCompletionItem(it.mavenId.key) }
      .filter { it.groupId != null && it.artifactId != null }
      .filter { searchString.isEmpty() || "${it.groupId}:${it.artifactId}".contains(searchString, ignoreCase = true) }
      .map { DependencyCompletionResult(it.groupId!!, it.artifactId!!, it.version ?: "", source = source) }
    MavenLog.LOG.debug("Project: get local maven artifacts finished: ${result.size}")
    return result
  }

  override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<DependencyPartCompletionResult> {
    val project = request.context.project
    val prefix = request.groupPrefix
    return MavenProjectsManager.getInstance(project).projects
      .asSequence()
      .filter { request.artifact.isEmpty() || it.mavenId.artifactId == request.artifact }
      .mapNotNull { it.mavenId.groupId }
      .distinct()
      .filter { prefix.isEmpty() || it.contains(prefix, ignoreCase = true) }
      .map { DependencyPartCompletionResult(it, source) }
      .toList()
  }

  override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<DependencyPartCompletionResult> {
    val project = request.context.project
    val prefix = request.artifactPrefix
    return MavenProjectsManager.getInstance(project).projects.asSequence()
      .filter { it.mavenId.groupId == request.group }
      .mapNotNull { it.mavenId.artifactId }
      .distinct()
      .filter { prefix.isEmpty() || it.contains(prefix, ignoreCase = true) }
      .map { DependencyPartCompletionResult(it, source) }
      .toList()
  }

  override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<DependencyPartCompletionResult> {
    val project = request.context.project
    val prefix = request.versionPrefix
    return MavenProjectsManager.getInstance(project).projects.asSequence()
      .filter { it.mavenId.groupId == request.group && it.mavenId.artifactId == request.artifact }
      .mapNotNull { it.mavenId.version }
      .distinct()
      .filter { prefix.isEmpty() || it.contains(prefix, ignoreCase = true) }
      .map { DependencyPartCompletionResult(it, source) }
      .toList()
  }
}