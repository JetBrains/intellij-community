// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContextImpl
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import org.jetbrains.idea.maven.completion.MavenDependencySearchContributor
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.jetbrains.idea.maven.utils.MavenUtil
import java.nio.file.Path
import java.util.function.Consumer
import kotlinx.coroutines.flow.toList

internal class MavenDependencySearchContributorImpl : MavenDependencySearchContributor {

  private fun createContext(project: Project) = DependencyCompletionContextImpl(
    Path.of(project.basePath ?: "").getEelDescriptor(),
    MavenUtil.SYSTEM_ID,
  )

  override suspend fun fulltextSearch(
      project: Project,
      searchString: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>,
  ) {
    collectGrouped(DependencyCompletionRequest(searchString, createContext(project))) { gId, aId, versions ->
      consumer.accept(MavenRepoArtifactInfo(gId, aId, versions))
    }
  }

  override suspend fun suggestPrefix(
      project: Project,
      groupId: String,
      artifactId: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>,
  ) {
    collectGrouped(DependencyCompletionRequest("$groupId:$artifactId", createContext(project))) { gId, aId, versions ->
      consumer.accept(MavenRepoArtifactInfo(gId, aId, versions))
    }
  }

  override suspend fun getGroupIds(project: Project, pattern: String?): Set<String> {
    val request = DependencyGroupCompletionRequest(pattern ?: "", "", createContext(project))
    return service<DependencyCompletionService>().suggestGroupCompletions(request).toList().map { it.result }.toSet()
  }

  override suspend fun getArtifactIds(project: Project, groupId: String): Set<String> {
    val request = DependencyArtifactCompletionRequest(groupId, "", createContext(project))
    return service<DependencyCompletionService>().suggestArtifactCompletions(request).toList().map { it.result }.toSet()
  }

  override suspend fun getVersions(project: Project, groupId: String, artifactId: String): Set<String> {
    val request = DependencyVersionCompletionRequest(groupId, artifactId, "", createContext(project))
    return service<DependencyCompletionService>().suggestVersionCompletions(request).toList().map { it.result }.toSet()
  }

  private suspend fun collectGrouped(
    request: DependencyCompletionRequest,
    consumerAccept: (String, String, List<String>) -> Unit,
  ) {
    val grouped = mutableMapOf<String, MutableList<String>>()
    val coords = mutableMapOf<String, Pair<String, String>>()
    service<DependencyCompletionService>().suggestCompletions(request).collect { r ->
      val key = "${r.groupId}:${r.artifactId}"
      coords[key] = r.groupId to r.artifactId
      grouped.getOrPut(key) { mutableListOf() }.add(r.version)
    }
    for ((key, versions) in grouped) {
      val (gId, aId) = coords[key]!!
      consumerAccept(gId, aId, versions)
    }
  }
}