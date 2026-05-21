// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor


import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.maven.completion.getCompletionContext
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.lookup.DependencyCompletionFuzzyMatcher
import com.intellij.repository.search.completion.lookup.StrictOrderWeigher
import com.intellij.util.xml.DomManager
import com.intellij.util.xml.GenericDomValue
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel

abstract class MavenCoordinateCompletionContributor protected constructor(private val myTagId: String) : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.completionType != CompletionType.BASIC) return
    val placeChecker = MavenCoordinateCompletionPlaceChecker(myTagId, parameters).checkPlace()
    if (!placeChecker.isCorrectPlace) return
    result.stopHere()
    val coordinates = placeChecker.coordinates!!
    val completionPrefix = CompletionUtil.findReferenceOrAlphanumericPrefix(parameters)
    val amendedResult = amendResultSet(result, completionPrefix)
    val context = parameters.getCompletionContext()
    runBlockingCancellable {
      fill(service<DependencyCompletionService>(), coordinates, context, amendedResult, completionPrefix)
      fillAfter(amendedResult)
    }
  }

  protected abstract suspend fun fill(service: DependencyCompletionService,
                                      coordinates: MavenDomShortArtifactCoordinates,
                                      context: DependencyCompletionContext,
                                      result: CompletionResultSet,
                                      completionPrefix: String)

  protected open fun fillAfter(result: CompletionResultSet) {
  }

  protected open fun amendResultSet(result: CompletionResultSet, completionPrefix: String): CompletionResultSet {
    result.restartCompletionWhenNothingMatches()
    return result
      .withPrefixMatcher(DependencyCompletionFuzzyMatcher(completionPrefix))
      .withRelevanceSorter(CompletionSorter.emptySorter().weigh(StrictOrderWeigher()))
  }

  protected fun isCorrectPlace(parameters: CompletionParameters): Boolean =
    MavenCoordinateCompletionPlaceChecker(myTagId, parameters).checkPlace().isCorrectPlace

  companion object {
    val MAVEN_COORDINATE_COMPLETION_PREFIX_KEY: Key<String> = Key.create("MAVEN_COORDINATE_COMPLETION_PREFIX_KEY")

    fun trimDummy(value: String?): String {
      if (value == null) {
        return ""
      }
      return StringUtil.trim(value.replace(CompletionUtil.DUMMY_IDENTIFIER, "").replace(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED, ""))
    }
  }
}

internal class MavenCoordinateCompletionPlaceChecker(private val myTagId: String, private val myParameters: CompletionParameters) {
  private var badPlace = false
  var project: Project? = null
    private set
  var coordinates: MavenDomShortArtifactCoordinates? = null
    private set

  val isCorrectPlace: Boolean
    get() = !badPlace

  fun checkPlace(): MavenCoordinateCompletionPlaceChecker {
    if (myParameters.completionType != CompletionType.BASIC) {
      badPlace = true
      return this
    }

    val element = myParameters.position

    val xmlText = element.parent
    if (xmlText !is XmlText) {
      badPlace = true
      return this
    }

    val tagElement = xmlText.getParent()

    if (tagElement !is XmlTag) {
      badPlace = true
      return this
    }

    if (myTagId != tagElement.name) {
      badPlace = true
      return this
    }

    project = element.project

    when (myTagId) {
      "artifactId", "groupId", "version" -> checkPlaceForChildrenTags(tagElement)
      "dependency", "extension", "plugin" -> checkPlaceForParentTags(tagElement)
      else -> badPlace = true
    }
    return this
  }

  private fun checkPlaceForChildrenTags(tag: XmlTag) {
    val domElement = DomManager.getDomManager(project).getDomElement(tag)

    if (domElement !is GenericDomValue<*>) {
      badPlace = true
      return
    }

    val parent = domElement.getParent()
    if (parent is MavenDomShortArtifactCoordinates && parent !is MavenDomProjectModel) {
      coordinates = parent
    }
    else {
      badPlace = true
    }
  }

  private fun checkPlaceForParentTags(tag: XmlTag) {
    val domElement = DomManager.getDomManager(project).getDomElement(tag)

    if (domElement is MavenDomShortArtifactCoordinates) {
      coordinates = domElement
    }
    else {
      badPlace = true
    }
  }
}
