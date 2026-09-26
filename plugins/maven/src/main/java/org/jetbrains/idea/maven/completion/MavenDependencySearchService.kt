// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import java.util.function.Consumer

@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
@ApiStatus.Obsolete
// Prefer using DependencyCompletionService
internal class MavenDependencySearchService(private val project: Project) {

  private val providers: List<MavenDependencySearchContributor>
    get() = MavenDependencySearchContributor.EP_NAME.extensionList

  suspend fun fulltextSearch(
      searchString: String,
      useCache: Boolean,
      useLocalOnly: Boolean,
      consumer: Consumer<MavenRepoArtifactInfo>
  ) {
    providers.forEach { it.fulltextSearch(project, searchString, useCache, useLocalOnly, consumer) }
  }

  suspend fun getGroupIds(pattern: String?): Set<String> {
    return providers.flatMap { it.getGroupIds(project, pattern) }.toSet()
  }

  suspend fun getArtifactIds(groupId: String): Set<String> {
    return providers.flatMap { it.getArtifactIds(project, groupId) }.toSet()
  }

  suspend fun getVersions(groupId: String, artifactId: String): Set<String> {
    return providers.flatMap { it.getVersions(project, groupId, artifactId) }.toSet()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): MavenDependencySearchService = project.service()
  }
}