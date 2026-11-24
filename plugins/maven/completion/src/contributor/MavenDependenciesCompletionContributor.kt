// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DEFINITION
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DOCUMENTATION
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.maven.completion.MAVEN_DEPENDENCY_COMPLETION
import com.intellij.maven.completion.icon
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigherData
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionHandler
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo


class MavenDependenciesCompletionContributor : MavenCoordinateCompletionContributor("dependency") {

  override suspend fun fill(
    service: DependencyCompletionService,
    coordinates: MavenDomShortArtifactCoordinates,
    context: DependencyCompletionContext,
    result: CompletionResultSet,
    completionPrefix: String,
  ) {
    val text = trimDummy(coordinates.xmlTag?.value?.text)
    var index = 0
    service.suggestCompletions(DependencyCompletionRequest(text, context)).collect { event ->
      if (event !is DependencyCompletionEvent.Item) return@collect
      val item = event.result
      val info = MavenRepoArtifactInfo(item.groupId, item.artifactId, listOf(item.version))
      result.addElement(
        MLRankingIgnorable.wrap(
          MavenDependencyCompletionUtil.lookupElement(info)
            .withIcon(item.icon)
            .withInsertHandler(MavenDependencyInsertionHandler.INSTANCE)
            .also {
              it.putUserData(StrictOrderWeigher.ORDER_KEY, StrictOrderWeigherData(item.source, index++))
              it.putUserData(SUPPRESS_QUICK_DEFINITION, true)
              it.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)
              it.putUserData(MAVEN_DEPENDENCY_COMPLETION, true)
            }
        )
      )
    }
  }
}
