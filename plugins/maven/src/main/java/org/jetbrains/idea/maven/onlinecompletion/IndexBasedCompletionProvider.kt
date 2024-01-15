// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.onlinecompletion

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.indices.MavenIndicesManager
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.reposearch.DependencySearchProvider
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import kotlin.math.min

/**
 * This class is used as a solution to support completion from repositories, which do not support online completion
 */
internal class IndexBasedCompletionProvider(private val myProject: Project) : DependencySearchProvider {

  override suspend fun fulltextSearch(searchString: String): List<RepositoryArtifactData> =
    search(MavenId(searchString))

  override suspend fun suggestPrefix(groupId: String, artifactId: String): List<RepositoryArtifactData> =
    search(MavenId(groupId, artifactId, null))

  private fun search(mavenId: MavenId): List<RepositoryArtifactData> {
    MavenLog.LOG.debug("Index: get local maven artifacts started")
    val result = buildList {
      val index = MavenIndicesManager.getInstance(myProject).getCommonGavIndex()
      for (groupId in index.groupIds) {
        if (groupId == null) continue
        if (!mavenId.groupId.isNullOrEmpty() && !nonExactMatches(groupId, mavenId.groupId!!)) {
          continue
        }
        for (artifactId in index.getArtifactIds(groupId)) {
          if (!mavenId.artifactId.isNullOrEmpty() && !nonExactMatches(artifactId, mavenId.artifactId!!)) {
            continue
          }
          if (artifactId == null) continue
          val info = MavenRepositoryArtifactInfo(groupId, artifactId, index.getVersions(groupId, artifactId))
          add(info)
          MavenLog.LOG.debug("Index: local maven artifact found ${info.groupId}:${info.artifactId}, completions: ${info.items.size}")
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
      return StringUtil.startsWith(template, real) || StringUtil.startsWith(
        real, template)
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

  override fun isLocal() = true
  override val cacheKey = "IndexBasedCompletionProvider" // assuming there's only one IndexBasedCompletionProvider per project
}