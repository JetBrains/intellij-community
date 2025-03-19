// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.completion.MavenCoordinateCompletionContributor.Companion.trimDummy
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenTopLevelDependencyInsertionHandler
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.idea.reposearch.SearchParameters
import java.util.concurrent.ConcurrentLinkedDeque

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

    val cld = ConcurrentLinkedDeque<MavenRepositoryArtifactInfo>()
    val promise = find(xmlText.project, trimDummy(xmlText.value), parameters, cld)
    while (promise.state == Promise.State.PENDING || !cld.isEmpty()) {
      ProgressManager.checkCanceled()
      val item = cld.poll()
      if (item != null) {
        result
          .addElement(MavenDependencyCompletionUtil.lookupElement(item).withInsertHandler(MavenTopLevelDependencyInsertionHandler.INSTANCE))
      }
    }

  }

  protected fun find(project: Project,
                     text: String,
                     parameters: CompletionParameters,
                     cld: ConcurrentLinkedDeque<MavenRepositoryArtifactInfo>): Promise<Int> {
    val searchParameters = createSearchParameters(parameters)
    val searchString: String = trimDummy(text)
    val service = DependencySearchService.getInstance(project)
    val splitted = searchString.split(':')
    if (splitted.size < 2) {
      return service.fulltextSearch(searchString, searchParameters) { (it as? MavenRepositoryArtifactInfo)?.let { cld.add(it) } }
    }
    return service.suggestPrefix(splitted[0], splitted[1], searchParameters) { (it as? MavenRepositoryArtifactInfo)?.let { cld.add(it) } }
  }

  private fun createSearchParameters(parameters: CompletionParameters): SearchParameters {
    return SearchParameters(parameters.invocationCount < 2, MavenUtil.isMavenUnitTestModeEnabled())
  }
}
