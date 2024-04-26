package org.jetbrains.idea.reposearch

interface DependencySearchProvider {
  suspend fun fulltextSearch(searchString: String): List<RepositoryArtifactData>

  suspend fun suggestPrefix(groupId: String, artifactId: String): List<RepositoryArtifactData>

  fun isLocal(): Boolean

  val cacheKey: String
}
