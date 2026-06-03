// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DEFINITION
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DOCUMENTATION
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.maven.completion.icon
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsContexts
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigherData
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionHandler
import org.jetbrains.idea.maven.indices.IndicesBundle
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo

class MavenGroupIdCompletionContributor : MavenCoordinateCompletionContributor("groupId") {

  override fun handleEmptyLookup(parameters: CompletionParameters, editor: Editor): @NlsContexts.HintText String? {
    return if (isCorrectPlace(parameters)) {
      IndicesBundle.message("maven.dependency.completion.group.empty")
    }
    else null
  }

  override suspend fun fill(service: DependencyCompletionService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            context: DependencyCompletionContext,
                            result: CompletionResultSet,
                            completionPrefix: String) {
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    var index = 0
    val addedGroupIds = mutableSetOf<String>()
    if (MavenAbstractPluginExtensionCompletionContributor.isPluginOrExtension(coordinates) && groupId.isEmpty()) {
      MavenAbstractPluginExtensionCompletionContributor.findArtifactsInPluginGroups(service, artifactId, context) { grp, item, _ ->
        if (addedGroupIds.add(grp)) {
          result.addElement(buildLookup(MavenRepoArtifactInfo(grp, artifactId, emptyList()), grp, item.source, index++, completionPrefix))
        }
      }
    }
    val grouped = mutableMapOf<String, MutableList<String>>()
    val sources = mutableMapOf<String, DependencyCompletionContributionSource>()
    service.suggestCompletions(DependencyCompletionRequest(groupId, context)).collect { event ->
      if (event !is DependencyCompletionEvent.Item) return@collect
      val item = event.result
      if ((artifactId.isEmpty() || item.artifactId == artifactId) && item.groupId !in addedGroupIds) {
        grouped.getOrPut(item.groupId) { mutableListOf() }.add(item.version)
        sources.putIfAbsent(item.groupId, item.source)
      }
    }
    for ((grp, versions) in grouped) {
      val source = sources[grp]!!
      result.addElement(buildLookup(MavenRepoArtifactInfo(grp, artifactId, versions), grp, source, index++, completionPrefix))
    }
  }

  private fun buildLookup(info: MavenRepoArtifactInfo, displayText: String,
                          source: DependencyCompletionContributionSource, index: Int,
                          completionPrefix: String) =
    MLRankingIgnorable.wrap(MavenDependencyCompletionUtil.lookupElement(info, displayText)
      .withIcon(source.icon)
      .withInsertHandler(MavenDependencyInsertionHandler.INSTANCE)
      .also {
        it.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix)
        it.putUserData(StrictOrderWeigher.ORDER_KEY, StrictOrderWeigherData(source, index))
        it.putUserData(SUPPRESS_QUICK_DEFINITION, true)
        it.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)
      })
}
