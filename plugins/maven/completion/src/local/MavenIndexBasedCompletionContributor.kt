// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.local

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionContributor
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import kotlin.math.min

internal class MavenIndexBasedCompletionContributor : DependencyCompletionContributor {
  override val source: DependencyCompletionContributionSource = DependencyCompletionContributionSource.LOCAL

  override val buildSystemId: ProjectSystemId = MavenUtil.SYSTEM_ID

  override fun isEnabled(): Boolean {
    return Registry.`is`("maven.dependency.completion.contributor.local.index")
  }

  private fun gavIndex(project: Project) =
    MavenIndicesManager.getInstance(project).getCommonGavIndex()

  override suspend fun search(request: DependencyCompletionRequest): List<DependencyCompletionResult> {
    val index = gavIndex(request.context.project)
    val searchString = request.searchString
    val (groupQuery, artifactQuery) = if (searchString.contains(":")) {
      val parts = searchString.split(":")
      parts[0] to parts.getOrElse(1) { "" }
    }
    else {
      searchString to ""
    }
    MavenLog.LOG.debug("Index: get local maven artifacts started")
    val result = buildList {
      for (groupId in index.groupIds) {
        if (groupQuery.isNotEmpty() && !nonExactMatches(groupId, groupQuery)) continue
        for (artifactId in index.getArtifactIds(groupId)) {
          if (artifactQuery.isNotEmpty() && !nonExactMatches(artifactId, artifactQuery)) continue
          for (version in index.getVersions(groupId, artifactId)) {
            add(DependencyCompletionResult(groupId, artifactId, version, source = source))
          }
          MavenLog.LOG.debug("Index: local maven artifact found $groupId:$artifactId")
        }
      }
    }
    MavenLog.LOG.debug("Index: get local maven artifacts finished")
    return result
  }

  private fun nonExactMatches(template: String, real: String): Boolean {
    val splittedTemplate = template.split(delimiters = charArrayOf('-', '.'))
    val splittedReal = real.split(delimiters = charArrayOf('-', '.'))
    if (splittedTemplate.size == 1 || splittedReal.size == 1) {
      return StringUtil.startsWith(template, real) || StringUtil.startsWith(real, template)
    }
    var matches = 0
    for (i in 0 until min(splittedReal.size, splittedTemplate.size)) {
      if (StringUtil.startsWith(splittedTemplate[i], splittedReal[i]) ||
          StringUtil.startsWith(splittedReal[i], splittedTemplate[i])) {
        matches += 1
      }
      if (matches >= 2) return true
    }
    return false
  }

  override suspend fun getGroups(request: DependencyGroupCompletionRequest): List<DependencyPartCompletionResult> {
    val index = gavIndex(request.context.project)
    val prefix = request.groupPrefix
    return index.groupIds
      .filter { prefix.isEmpty() || it.contains(prefix, ignoreCase = true) }
      .filter { request.artifact.isEmpty() || index.getArtifactIds(it).contains(request.artifact) }
      .map { DependencyPartCompletionResult(it, source) }
  }

  override suspend fun getArtifacts(request: DependencyArtifactCompletionRequest): List<DependencyPartCompletionResult> {
    val index = gavIndex(request.context.project)
    val prefix = request.artifactPrefix
    val artifactIds = if (request.group.isEmpty()) {
      index.groupIds.flatMapTo(mutableSetOf()) { index.getArtifactIds(it) }
    }
    else {
      index.getArtifactIds(request.group)
    }
    return artifactIds
      .filter { prefix.isEmpty() || it.contains(prefix, ignoreCase = true) }
      .map { DependencyPartCompletionResult(it, source) }
  }

  override suspend fun getVersions(request: DependencyVersionCompletionRequest): List<DependencyPartCompletionResult> {
    val index = gavIndex(request.context.project)
    val prefix = request.versionPrefix
    return index.getVersions(request.group, request.artifact)
      .filter { prefix.isEmpty() || it.contains(prefix, ignoreCase = true) }
      .map { DependencyPartCompletionResult(it, source) }
  }
}