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

internal class MavenLegacyDependencySearchContributor(private val project: Project) : MavenDependencySearchContributor {
  private val service: DependencySearchService
    get() = DependencySearchService.getInstance(project)

  override fun suggestPrefixBlocking(
      groupId: String,
      artifactId: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>,
  ): Promise<Int> {
    val parameters = SearchParameters(useCache, useLocalOnly)
    return service.suggestPrefix(groupId, artifactId, parameters, consumer.toLegacy())
  }

  override fun fulltextSearchBlocking(
      searchString: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>,
  ): Promise<Int> {
    val parameters = SearchParameters(useCache, useLocalOnly)
    return service.fulltextSearch(searchString, parameters, consumer.toLegacy())
  }

  override suspend fun fulltextSearch(
      searchString: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>,
  ) {
    val parameters = SearchParameters(useCache, useLocalOnly)
    service.fulltextSearchAsync(searchString, parameters, consumer.toLegacy())
  }

  override suspend fun suggestPrefix(
      groupId: String,
      artifactId: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>,
  ) {
    val parameters = SearchParameters(useCache, useLocalOnly)
    service.suggestPrefixAsync(groupId, artifactId, parameters, consumer.toLegacy())
  }

  override fun getGroupIdsBlocking(pattern: String?): Set<String> {
    return service.getGroupIds(pattern)
  }

  override suspend fun getGroupIds(pattern: String?): Set<String> {
    return service.getGroupIds(pattern)
  }

  override fun getArtifactIdsBlocking(groupId: String): Set<String> {
    return service.getArtifactIds(groupId)
  }

  override suspend fun getArtifactIds(groupId: String): Set<String> {
    return service.getArtifactIds(groupId)
  }

  override fun getVersionsBlocking(groupId: String, artifactId: String): Set<String> {
    return service.getVersions(groupId, artifactId)
  }

  override suspend fun getVersions(groupId: String, artifactId: String): Set<String> {
    return service.getVersionsAsync(groupId, artifactId)
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