// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DEFINITION
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DOCUMENTATION
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.maven.completion.icon
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigherData
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenArtifactIdInsertionHandler
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo

class MavenArtifactIdCompletionContributor : MavenCoordinateCompletionContributor("artifactId") {

  override suspend fun fill(service: DependencyCompletionService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            context: DependencyCompletionContext,
                            result: CompletionResultSet,
                            completionPrefix: String) {
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    if (MavenAbstractPluginExtensionCompletionContributor.isPluginOrExtension(coordinates) && groupId.isEmpty()) {
      MavenAbstractPluginExtensionCompletionContributor.findArtifactsInPluginGroups(service, artifactId, context) { grp, item, index ->
        val info = MavenRepoArtifactInfo(grp, item.result, emptyList())
        result.addElement(buildLookup(info, completionPrefix, item.source, index))
      }
      return
    }
    var index = 0
    service.suggestArtifactCompletions(DependencyArtifactCompletionRequest(groupId, artifactId, context)).collect { event ->
      if (event !is DependencyCompletionEvent.Item) return@collect
      val item = event.result
      val info = MavenRepoArtifactInfo(groupId, item.result, emptyList())
      result.addElement(buildLookup(info, completionPrefix, item.source, index++))
    }
  }

  private fun buildLookup(info: MavenRepoArtifactInfo, completionPrefix: String,
                          source: DependencyCompletionContributionSource,
                          index: Int) =
    MLRankingIgnorable.wrap(MavenDependencyCompletionUtil.lookupElement(info, info.artifactId)
      .withIcon(source.icon)
      .withInsertHandler(MavenArtifactIdInsertionHandler.INSTANCE)
      .also {
        it.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix)
        it.putUserData(StrictOrderWeigher.ORDER_KEY, StrictOrderWeigherData(source, index))
        it.putUserData(SUPPRESS_QUICK_DEFINITION, true)
        it.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)
      })
}
