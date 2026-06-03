// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DEFINITION
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DOCUMENTATION
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.maven.completion.icon
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.MavenVersionNegatingWeigher
import org.jetbrains.idea.maven.server.MavenServerManager

open class MavenVersionCompletionContributor : MavenCoordinateCompletionContributor("version") {

  override suspend fun fill(service: DependencyCompletionService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            context: DependencyCompletionContext,
                            result: CompletionResultSet,
                            completionPrefix: String) {
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    if (MavenAbstractPluginExtensionCompletionContributor.isPluginOrExtension(coordinates) && groupId.isEmpty()) {
      for (pluginGroupId in MavenAbstractPluginExtensionCompletionContributor.PLUGIN_GROUPS) {
        service.suggestVersionCompletions(DependencyVersionCompletionRequest(pluginGroupId, artifactId, "", context)).collect { event ->
          if (event !is DependencyCompletionEvent.Item) return@collect
          val item = event.result
          result.addElement(buildLookup(item.result, item.source, completionPrefix))
        }
      }
      return
    }
    service.suggestVersionCompletions(DependencyVersionCompletionRequest(groupId, artifactId, "", context)).collect { event ->
      if (event !is DependencyCompletionEvent.Item) return@collect
      val item = event.result
      result.addElement(buildLookup(item.result, item.source, completionPrefix))
    }
  }

  override fun fillAfter(result: CompletionResultSet) {
    if (MavenServerManager.getInstance().isUseMaven2) {
      result.addElement(LookupElementBuilder.create("RELEASE").withStrikeoutness(true))
      result.addElement(LookupElementBuilder.create("LATEST").withStrikeoutness(true))
    }
  }

  private fun buildLookup(version: String, source: DependencyCompletionContributionSource,
                          completionPrefix: String) =
    MLRankingIgnorable.wrap(LookupElementBuilder.create(version)
      .withIcon(source.icon)
      .also {
        it.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix)
        it.putUserData(SUPPRESS_QUICK_DEFINITION, true)
        it.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)
      })

  override fun amendResultSet(result: CompletionResultSet, completionPrefix: String): CompletionResultSet {
    result.restartCompletionWhenNothingMatches()
    return result.withRelevanceSorter(CompletionService.getCompletionService().emptySorter().weigh(MavenVersionNegatingWeigher()))
  }
}
