// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DEFINITION
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DOCUMENTATION
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.maven.completion.icon
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyPartCompletionResult
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigherData
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomExtension
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionHandler
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo


abstract class MavenAbstractPluginExtensionCompletionContributor(tagName: String) : MavenCoordinateCompletionContributor(tagName) {

  override suspend fun fill(service: DependencyCompletionService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            context: DependencyCompletionContext,
                            result: CompletionResultSet,
                            completionPrefix: String) {
    val text = trimDummy(coordinates.xmlTag?.value?.text)
    var index = 0
    service.suggestCompletions(DependencyCompletionRequest(text, context)).collect { event ->
      if (event !is DependencyCompletionEvent.Item) return@collect
      val item = event.result
      val info = MavenRepoArtifactInfo(item.groupId, item.artifactId, listOf(item.version))
      result.addElement(
        MLRankingIgnorable.wrap(MavenDependencyCompletionUtil.lookupElement(info)
          .withIcon(item.source.icon)
          .withInsertHandler(MavenDependencyInsertionHandler.INSTANCE)
          .also {
            it.putUserData(StrictOrderWeigher.ORDER_KEY, StrictOrderWeigherData(item.source, index++))
            it.putUserData(SUPPRESS_QUICK_DEFINITION, true)
            it.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)
          })
      )
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
                                            consumer: (groupId: String, item: DependencyPartCompletionResult, index: Int) -> Unit) {
      //todo: read groups from maven settings.xml
      var index = 0
      for (groupId in PLUGIN_GROUPS) {
        service.suggestArtifactCompletions(DependencyArtifactCompletionRequest(groupId, artifactPrefix, context)).collect { event ->
          if (event !is DependencyCompletionEvent.Item) return@collect
          consumer(groupId, event.result, index++)
        }
      }
    }
  }
}
