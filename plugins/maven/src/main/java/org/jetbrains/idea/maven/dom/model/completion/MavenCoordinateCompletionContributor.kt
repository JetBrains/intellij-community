// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.util.xml.DomManager
import com.intellij.util.xml.GenericDomValue
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionHandler
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.idea.reposearch.DependencySearchService.Companion.getInstance
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import org.jetbrains.idea.reposearch.SearchParameters
import java.util.function.Consumer
import java.util.function.Predicate

abstract class MavenCoordinateCompletionContributor protected constructor(private val myTagId: String) : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.completionType != CompletionType.BASIC) return
    val placeChecker = MavenCoordinateCompletionPlaceChecker(myTagId, parameters).checkPlace()

    if (placeChecker.isCorrectPlace) {
      val coordinates = placeChecker.coordinates!!
      val completionPrefix = CompletionUtil.findReferenceOrAlphanumericPrefix(parameters)
      val amendedResult = amendResultSet(result)

      runBlockingCancellable {
        find(getInstance(placeChecker.project!!), coordinates, parameters) {
          fillResults(amendedResult, coordinates, it, completionPrefix)
        }
        fillAfter(amendedResult)
      }
    }
  }

  protected open fun fillResults(result: CompletionResultSet,
                                 coordinates: MavenDomShortArtifactCoordinates,
                                 item: RepositoryArtifactData,
                                 completionPrefix: String) {
    if (item is MavenRepositoryArtifactInfo) {
      fillResult(coordinates, result, item, completionPrefix)
    }
  }

  protected fun createSearchParameters(parameters: CompletionParameters): SearchParameters {
    return SearchParameters(parameters.invocationCount < 2, MavenUtil.isMavenUnitTestModeEnabled())
  }

  protected abstract suspend fun find(service: DependencySearchService,
                                      coordinates: MavenDomShortArtifactCoordinates,
                                      parameters: CompletionParameters,
                                      consumer: Consumer<RepositoryArtifactData>)

  protected open fun fillAfter(result: CompletionResultSet) {
  }

  protected open fun fillResult(coordinates: MavenDomShortArtifactCoordinates,
                                result: CompletionResultSet,
                                item: MavenRepositoryArtifactInfo,
                                completionPrefix: String) {
    val lookup: LookupElement = MavenDependencyCompletionUtil.lookupElement(item)
      .withInsertHandler(MavenDependencyInsertionHandler.INSTANCE)
    lookup.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix)
    result.addElement(lookup)
  }

  protected open fun amendResultSet(result: CompletionResultSet): CompletionResultSet {
    result.restartCompletionWhenNothingMatches()
    return result
  }

  protected fun <T> withPredicate(consumer: Consumer<in T>,
                                  predicate: Predicate<in T>): Consumer<T> {
    return Consumer { it: T ->
      if (predicate.test(it)) {
        consumer.accept(it)
      }
    }
  }

  protected fun isCorrectPlace(parameters: CompletionParameters) =
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
    if (parent is MavenDomShortArtifactCoordinates) {
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