// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.maven.completion.contributor.insert.MavenTopLevelDependencyInsertionHandler
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.completion.MavenCoordinateCompletionContributor.Companion.trimDummy
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.completion.MavenDependencySearchService
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

    val cld = ConcurrentLinkedDeque<MavenRepoArtifactInfo>()
    runBlockingCancellable { find(xmlText.project, trimDummy(xmlText.value), parameters, cld) }
    for (item in cld) {
      result.addElement(MavenDependencyCompletionUtil.lookupElement(item).withInsertHandler(MavenTopLevelDependencyInsertionHandler.INSTANCE))
    }

  }

  protected suspend fun find(project: Project,
                             text: String,
                             parameters: CompletionParameters,
                             cld: ConcurrentLinkedDeque<MavenRepoArtifactInfo>) {
    val searchString: String = trimDummy(text)
    val service = MavenDependencySearchService.getInstance(project)
    val splitted = searchString.split(':')
    val useCache = parameters.invocationCount < 2
    val useLocalOnly = MavenUtil.isMavenUnitTestModeEnabled()
    if (splitted.size < 2) {
      service.fulltextSearch(searchString, useCache, useLocalOnly) { cld.add(it) }
    } else {
      service.suggestPrefix(splitted[0], splitted[1], useCache, useLocalOnly) { cld.add(it) }
    }
  }
}
