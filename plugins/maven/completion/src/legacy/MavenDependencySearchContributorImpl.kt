// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.legacy

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContextImpl
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyGroupCompletionRequest
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import kotlinx.coroutines.flow.toList
import org.jetbrains.idea.maven.completion.MavenDependencySearchContributor
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.function.Consumer

internal class MavenDependencySearchContributorImpl : MavenDependencySearchContributor {

  private fun createContext(project: Project) = DependencyCompletionContextImpl(project, MavenUtil.SYSTEM_ID)

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

  override suspend fun getGroupIds(project: Project, pattern: String?): Set<String> {
    val request = DependencyGroupCompletionRequest(pattern ?: "", "", createContext(project))
    return service<DependencyCompletionService>().suggestGroupCompletions(request).toList().mapNotNull { event ->
      if (event !is DependencyCompletionEvent.Item) return@mapNotNull null
      event.result.result
    }.toSet()
  }

  override suspend fun getArtifactIds(project: Project, groupId: String): Set<String> {
    val request = DependencyArtifactCompletionRequest(groupId, "", createContext(project))
    return service<DependencyCompletionService>().suggestArtifactCompletions(request).toList().mapNotNull { event ->
      if (event !is DependencyCompletionEvent.Item) return@mapNotNull null
      event.result.result
    }.toSet()
  }

  override suspend fun getVersions(project: Project, groupId: String, artifactId: String): Set<String> {
    val request = DependencyVersionCompletionRequest(groupId, artifactId, "", createContext(project))
    return service<DependencyCompletionService>().suggestVersionCompletions(request).toList().mapNotNull { event ->
      if (event !is DependencyCompletionEvent.Item) return@mapNotNull null
      event.result.result
    }.toSet()
  }

  private suspend fun collectGrouped(
    request: DependencyCompletionRequest,
    consumerAccept: (String, String, List<String>) -> Unit,
  ) {
    val grouped = mutableMapOf<Pair<String, String>, MutableList<String>>()

    service<DependencyCompletionService>().suggestCompletions(request).collect { event ->
      if (event !is DependencyCompletionEvent.Item) return@collect
      val r = event.result
      val key = r.groupId to r.artifactId
      grouped.getOrPut(key) { mutableListOf() }.add(r.version)
    }
    for ((coords, versions) in grouped) {
      val (groupId, artifactId) = coords
      consumerAccept(groupId, artifactId, versions)
    }
  }
}