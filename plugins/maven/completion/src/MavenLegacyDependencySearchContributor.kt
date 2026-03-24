// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion

import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.completion.MavenDependencySearchContributor
import org.jetbrains.idea.maven.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import org.jetbrains.idea.reposearch.SearchParameters
import java.util.function.Consumer

internal class MavenLegacyDependencySearchContributor : MavenDependencySearchContributor {

  override fun suggestPrefixBlocking(
      project: Project,
      groupId: String,
      artifactId: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>,
  ): Promise<Int> {
    val parameters = SearchParameters(useCache, useLocalOnly)
    return DependencySearchService.getInstance(project).suggestPrefix(groupId, artifactId, parameters, consumer.toLegacy())
  }

  override fun fulltextSearchBlocking(
      project: Project,
      searchString: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>,
  ): Promise<Int> {
    val parameters = SearchParameters(useCache, useLocalOnly)
    return DependencySearchService.getInstance(project).fulltextSearch(searchString, parameters, consumer.toLegacy())
  }

  override suspend fun fulltextSearch(
      project: Project,
      searchString: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>,
  ) {
    val parameters = SearchParameters(useCache, useLocalOnly)
    DependencySearchService.getInstance(project).fulltextSearchAsync(searchString, parameters, consumer.toLegacy())
  }

  override suspend fun suggestPrefix(
      project: Project,
      groupId: String,
      artifactId: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>,
  ) {
    val parameters = SearchParameters(useCache, useLocalOnly)
    DependencySearchService.getInstance(project).suggestPrefixAsync(groupId, artifactId, parameters, consumer.toLegacy())
  }

  override fun getGroupIdsBlocking(project: Project, pattern: String?): Set<String> {
    return DependencySearchService.getInstance(project).getGroupIds(pattern)
  }

  override suspend fun getGroupIds(project: Project, pattern: String?): Set<String> {
    return DependencySearchService.getInstance(project).getGroupIds(pattern)
  }

  override fun getArtifactIdsBlocking(project: Project, groupId: String): Set<String> {
    return DependencySearchService.getInstance(project).getArtifactIds(groupId)
  }

  override suspend fun getArtifactIds(project: Project, groupId: String): Set<String> {
    return DependencySearchService.getInstance(project).getArtifactIds(groupId)
  }

  override fun getVersionsBlocking(project: Project, groupId: String, artifactId: String): Set<String> {
    return DependencySearchService.getInstance(project).getVersions(groupId, artifactId)
  }

  override suspend fun getVersions(project: Project, groupId: String, artifactId: String): Set<String> {
    return DependencySearchService.getInstance(project).getVersionsAsync(groupId, artifactId)
  }

  private class LegacyMavenRepoArtifactInfo(groupId: String, artifactId: String, items: Array<MavenDependencyCompletionItem>) :
    MavenRepoArtifactInfo(groupId, artifactId, items), RepositoryArtifactData {

    constructor(info: MavenRepoArtifactInfo) : this(info.groupId, info.artifactId, info.items)

    override fun getKey() = "$groupId:$artifactId"

    override fun mergeWith(another: RepositoryArtifactData): RepositoryArtifactData {
      if (another !is MavenRepoArtifactInfo) {
        throw IllegalArgumentException()
      }
      return LegacyMavenRepoArtifactInfo(groupId, artifactId, items + another.items)
    }
  }

  private fun Consumer<MavenRepoArtifactInfo>.toLegacy(): Consumer<RepositoryArtifactData> =
      Consumer { data ->
          if (data is MavenRepoArtifactInfo) {
              accept(LegacyMavenRepoArtifactInfo(data))
          }
      }
}