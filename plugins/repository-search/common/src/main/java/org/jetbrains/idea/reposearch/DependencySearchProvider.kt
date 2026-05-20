package org.jetbrains.idea.reposearch

import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo

@Deprecated("Use DependencyCompletionContributor instead")
interface DependencySearchProvider {
  suspend fun fulltextSearch(searchString: String): List<MavenRepoArtifactInfo>

  suspend fun suggestPrefix(groupId: String, artifactId: String): List<MavenRepoArtifactInfo>

  fun isLocal(): Boolean

  val cacheKey: String
}
