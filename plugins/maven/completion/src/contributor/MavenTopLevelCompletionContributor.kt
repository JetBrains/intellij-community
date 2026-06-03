// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DEFINITION
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DOCUMENTATION
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.maven.completion.contributor.insert.MavenTopLevelDependencyInsertionHandler
import com.intellij.maven.completion.getCompletionContext
import com.intellij.maven.completion.icon
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.lookup.DependencyCompletionFuzzyMatcher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigherData
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo

abstract class MavenTopLevelCompletionContributor(val myName: String) : CompletionContributor() {


  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.completionType != CompletionType.BASIC) {
      return
    }
    val element = parameters.position
    val xmlText = element.parent as? XmlText ?: return
    val parent = xmlText.parent
    if (parent !is XmlTag || parent.name != myName) {
      return
    }

    val searchString = MavenCoordinateCompletionContributor.trimDummy(xmlText.value)
    if (parameters.invocationCount == 0 && searchString.length < 3) {
      // autocomplete only 3 or more chars
      return
    }

    result.stopHere()
    result.restartCompletionWhenNothingMatches()

    val request = DependencyCompletionRequest(searchString, parameters.getCompletionContext())
    val resultSet = result.withPrefixMatcher(DependencyCompletionFuzzyMatcher(searchString))
      .withRelevanceSorter(CompletionSorter.emptySorter().weigh(StrictOrderWeigher()))
    var index = 0
    runBlockingCancellable {
      service<DependencyCompletionService>()
        .suggestCompletions(request)
        .collect { event ->
          if (event !is DependencyCompletionEvent.Item) return@collect
          val item = event.result
          val lookupElement = MavenDependencyCompletionUtil.lookupElement(MavenRepoArtifactInfo(item.groupId, item.artifactId, listOf(item.version)))
              .withIcon(item.icon)
              .withInsertHandler(MavenTopLevelDependencyInsertionHandler.INSTANCE)
          lookupElement.putUserData(StrictOrderWeigher.ORDER_KEY, StrictOrderWeigherData(item.source, index++))
          lookupElement.putUserData(SUPPRESS_QUICK_DEFINITION, true)
          lookupElement.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)
          resultSet.addElement(MLRankingIgnorable.wrap(lookupElement))
        }
    }
  }
}
