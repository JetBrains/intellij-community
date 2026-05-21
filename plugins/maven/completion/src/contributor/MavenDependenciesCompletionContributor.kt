// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.maven.completion.icon
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigherData
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionHandler
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo


class MavenDependenciesCompletionContributor : MavenCoordinateCompletionContributor("dependency") {

  override suspend fun fill(service: DependencyCompletionService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            context: DependencyCompletionContext,
                            result: CompletionResultSet,
                            completionPrefix: String) {
    val text = trimDummy(coordinates.xmlTag?.value?.text)
    val grouped = mutableMapOf<Pair<String, String>, MutableList<String>>()
    val sources = mutableMapOf<Pair<String, String>, DependencyCompletionContributionSource>()
    service.suggestCompletions(DependencyCompletionRequest(text, context)).collect { item ->
      val key = item.groupId to item.artifactId
      grouped.getOrPut(key) { mutableListOf() }.add(item.version)
      sources.putIfAbsent(key, item.source)
    }
    var index = 0
    for ((coords, versions) in grouped) {
      val source = sources[coords]!!
      val info = MavenRepoArtifactInfo(coords.first, coords.second, versions)
      result.addElement(
        MLRankingIgnorable.wrap(MavenDependencyCompletionUtil.lookupElement(info)
          .withIcon(source.icon)
          .withInsertHandler(MavenDependencyInsertionHandler.INSTANCE)
          .also {
            it.putUserData(StrictOrderWeigher.ORDER_KEY, StrictOrderWeigherData(source, index++))
          })
      )
    }
  }
}
