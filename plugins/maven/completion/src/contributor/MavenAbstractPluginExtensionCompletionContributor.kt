// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import org.jetbrains.idea.maven.dom.model.MavenDomExtension
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo


abstract class MavenAbstractPluginExtensionCompletionContributor(tagName: String) : MavenCoordinateCompletionContributor(tagName) {

  override suspend fun find(service: DependencyCompletionService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            context: DependencyCompletionContext,
                            consumer: (MavenRepoArtifactInfo) -> Unit) {
    val text = trimDummy(coordinates.xmlTag?.value?.text)
    service.suggestCompletions(DependencyCompletionRequest(text, context)).collect { result ->
      consumer(MavenRepoArtifactInfo(result.groupId, result.artifactId, listOf(result.version)))
    }
  }

  companion object {
    val PLUGIN_GROUPS: List<String> = listOf("org.apache.maven.plugins", "org.codehaus.mojo")

    @JvmStatic
    fun isPluginOrExtension(coordinates: MavenDomShortArtifactCoordinates): Boolean {
      return coordinates is MavenDomPlugin || coordinates is MavenDomExtension
    }

    suspend fun findArtifactsInPluginGroups(service: DependencyCompletionService,
                                            artifactPrefix: String,
                                            context: DependencyCompletionContext,
                                            consumer: (MavenRepoArtifactInfo) -> Unit) {
      //todo: read groups from maven settings.xml
      for (groupId in PLUGIN_GROUPS) {
        service.suggestArtifactCompletions(DependencyArtifactCompletionRequest(groupId, artifactPrefix, context)).collect { result ->
          consumer(MavenRepoArtifactInfo(groupId, result.result, emptyList()))
        }
      }
    }
  }
}
