// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.onlinecompletion

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.indices.MavenIndex
import org.jetbrains.idea.maven.indices.MavenSearchIndex
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchProvider
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import java.util.function.Consumer

/**
 * This class is used as a solution to support completion from repositories, which do not support online completion
 */
internal class IndexBasedCompletionProvider(private val myIndex: MavenIndex) : DependencySearchProvider {
  override fun fulltextSearch(searchString: String,
                              consumer: Consumer<RepositoryArtifactData>) {
    val mavenId = MavenId(searchString)
    search(consumer, mavenId)
  }

  override fun suggestPrefix(groupId: String?,
                             artifactId: String?,
                             consumer: Consumer<RepositoryArtifactData>) {
    search(consumer, MavenId(groupId, artifactId, null))
  }

  private fun search(consumer: Consumer<RepositoryArtifactData>, mavenId: MavenId) {
    for (groupId in myIndex.groupIds) {
      if (groupId == null) continue
      if (!mavenId.groupId.isNullOrEmpty() && !nonExactMatches(groupId, mavenId.groupId!!)) {
        continue
      }
      for (artifactId in myIndex.getArtifactIds(groupId)) {
        if (!mavenId.artifactId.isNullOrEmpty() && !nonExactMatches(artifactId, mavenId.artifactId!!)) {
          continue
        }
        if (artifactId == null) continue
        val info = MavenRepositoryArtifactInfo(groupId, artifactId, myIndex.getVersions(groupId, artifactId))
        consumer.accept(info)
      }
    }
  }

  private fun nonExactMatches(template: String, real: String): Boolean {
    val splittedTemplate = template.split(delimiters = charArrayOf('-', '.'))
    val splittedReal = real.split(delimiters = charArrayOf('-', '.'))
    if (splittedTemplate.size == 1 || splittedReal.size == 1) {
      return StringUtil.startsWith(template, real) || StringUtil.startsWith(
        real, template)
    }
    var matches = 0
    for (i in 0 until Math.min(splittedReal.size, splittedTemplate.size)) {
      if (StringUtil.startsWith(splittedTemplate[i], splittedReal[i]) ||
          StringUtil.startsWith(splittedReal[i], splittedTemplate[i])) {
        matches += 1
      }
      if (matches >= 2) return true
    }
    return false
  }

  override fun isLocal() = true

  val index: MavenSearchIndex
    get() = myIndex
}