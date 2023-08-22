package org.jetbrains.idea.packagesearch

enum class SortMetric(private val representationName: String, val parameterName: String) {
  NONE("None", ""),
  GITHUB_STARS("GitHub stars", "github_stars"),
  STACKOVERFLOW_HEALTH("StackOverflow health", "stackoverflow_health"),
  DEPENDENCY_RATING("Dependency rating", "dependency_rating"),
  OSS_HEALTH("OSS health", "oss_health");

  override fun toString(): String {
    return representationName
  }
}