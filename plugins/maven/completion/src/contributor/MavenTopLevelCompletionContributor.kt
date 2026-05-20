// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.maven.completion.contributor.insert.MavenTopLevelDependencyInsertionHandler
import com.intellij.maven.completion.getCompletionContext
import com.intellij.maven.completion.icon
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.components.service
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.completion.MavenCoordinateCompletionContributor.Companion.trimDummy
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigherData

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

    result.restartCompletionWhenNothingMatches()

    val request = DependencyCompletionRequest(trimDummy(xmlText.value), parameters.getCompletionContext())
    val resultSet = result.withRelevanceSorter(CompletionSorter.emptySorter().weigh(StrictOrderWeigher()))
    var index = 0
    runBlockingCancellable {
      service<DependencyCompletionService>()
        .suggestCompletions(request)
        .collect { item ->
          val lookupElement = MavenDependencyCompletionUtil.lookupElement(MavenRepoArtifactInfo(item.groupId, item.artifactId, listOf(item.version)))
              .withIcon(item.icon)
              .withInsertHandler(MavenTopLevelDependencyInsertionHandler.INSTANCE)
          lookupElement.putUserData(StrictOrderWeigher.ORDER_KEY, StrictOrderWeigherData(item.source, index++))
          resultSet.addElement(MLRankingIgnorable.wrap(lookupElement))
        }
    }
  }
}
