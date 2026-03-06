// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.all
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import java.util.function.Consumer

/**
 * Encapsulates [org.jetbrains.idea.reposearch.DependencySearchService] for Maven plugin.
 * Other classes in the Maven plugin should depend on this service instead of DependencySearchService.
 */
@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class MavenDependencySearchService(private val project: Project) {

  private val providers: List<MavenDependencySearchContributor>
    get() = MavenDependencySearchContributor.EP_NAME.extensionList

  @Deprecated("prefer async method")
  fun suggestPrefixBlocking(
      groupId: String,
      artifactId: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>
  ): Promise<Int> {
    val promises = providers.map { it.suggestPrefixBlocking(groupId, artifactId, useCache, useLocalOnly, consumer) }
    if (promises.isEmpty()) return resolvedPromise(0)
    return promises.all().then { results ->
      var total = 0
      for (res in results as List<Int?>) {
        if (res != null) total += res
      }
      total
    }
  }

  @Deprecated("prefer async method")
  fun fulltextSearchBlocking(
      searchString: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>
  ): Promise<Int> {
    val promises = providers.map { it.fulltextSearchBlocking(searchString, useCache, useLocalOnly, consumer) }
    if (promises.isEmpty()) return resolvedPromise(0)
    return promises.all().then { results ->
      var total = 0
      for (res in results as List<Int?>) {
        if (res != null) total += res
      }
      total
    }
  }

  suspend fun fulltextSearch(
      searchString: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>
  ) {
    providers.forEach { it.fulltextSearch(searchString, useCache, useLocalOnly, consumer) }
  }

  suspend fun suggestPrefix(
      groupId: String,
      artifactId: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>
  ) {
    providers.forEach { it.suggestPrefix(groupId, artifactId, useCache, useLocalOnly, consumer) }
  }

  @Deprecated("prefer async method")
  fun getGroupIdsBlocking(pattern: String?): Set<String> {
    return providers.flatMap { it.getGroupIdsBlocking(pattern) }.toSet()
  }

  suspend fun getGroupIds(pattern: String?): Set<String> {
    return providers.flatMap { it.getGroupIds(pattern) }.toSet()
  }

  @Deprecated("prefer async method")
  fun getArtifactIdsBlocking(groupId: String): Set<String> {
    return providers.flatMap { it.getArtifactIdsBlocking(groupId) }.toSet()
  }

  suspend fun getArtifactIds(groupId: String): Set<String> {
    return providers.flatMap { it.getArtifactIds(groupId) }.toSet()
  }

  @Deprecated("prefer async method")
  fun getVersionsBlocking(groupId: String, artifactId: String): Set<String> {
    return providers.flatMap { it.getVersionsBlocking(groupId, artifactId) }.toSet()
  }

  suspend fun getVersions(groupId: String, artifactId: String): Set<String> {
    return providers.flatMap { it.getVersions(groupId, artifactId) }.toSet()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): MavenDependencySearchService = project.service()
  }
}