// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.MavenVersionNegatingWeigher
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.jetbrains.idea.maven.server.MavenServerManager

open class MavenVersionCompletionContributor : MavenCoordinateCompletionContributor("version") {

  override suspend fun find(service: DependencyCompletionService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            context: DependencyCompletionContext,
                            consumer: (MavenRepoArtifactInfo) -> Unit) {
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    if (MavenAbstractPluginExtensionCompletionContributor.isPluginOrExtension(coordinates) && groupId.isEmpty()) {
      for (pluginGroupId in MavenAbstractPluginExtensionCompletionContributor.PLUGIN_GROUPS) {
        service.suggestVersionCompletions(DependencyVersionCompletionRequest(pluginGroupId, artifactId, "", context)).collect { result ->
          consumer(MavenRepoArtifactInfo(pluginGroupId, artifactId, listOf(result.result)))
        }
      }
      return
    }
    service.suggestVersionCompletions(DependencyVersionCompletionRequest(groupId, artifactId, "", context)).collect { result ->
      consumer(MavenRepoArtifactInfo(groupId, artifactId, listOf(result.result)))
    }
  }

  override fun fillAfter(result: CompletionResultSet) {
    if (MavenServerManager.getInstance().isUseMaven2) {
      result.addElement(LookupElementBuilder.create("RELEASE").withStrikeoutness(true))
      result.addElement(LookupElementBuilder.create("LATEST").withStrikeoutness(true))
    }
  }

  override fun fillResult(coordinates: MavenDomShortArtifactCoordinates,
                          result: CompletionResultSet,
                          item: MavenRepoArtifactInfo,
                          completionPrefix: String) {
    val version = item.version ?: return
    val lookup = LookupElementBuilder.create(version)
    lookup.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix)
    result.addElement(lookup)
  }

  override fun amendResultSet(result: CompletionResultSet): CompletionResultSet {
    return result.withRelevanceSorter(CompletionService.getCompletionService().emptySorter().weigh(MavenVersionNegatingWeigher()))
  }
}
