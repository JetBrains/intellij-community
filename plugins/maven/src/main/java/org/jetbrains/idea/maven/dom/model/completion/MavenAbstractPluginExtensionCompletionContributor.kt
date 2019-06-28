// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.collectResults
import org.jetbrains.idea.maven.dom.model.MavenDomExtension
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters
import java.awt.SystemColor.info

import java.util.function.Consumer
import java.util.function.Predicate


abstract class MavenAbstractPluginExtensionCompletionContributor(tagName: String) : MavenCoordinateCompletionContributor(tagName) {
  override fun find(service: DependencySearchService,
                    coordinates: MavenDomShortArtifactCoordinates,
                    parameters: CompletionParameters,
                    consumer: Consumer<MavenRepositoryArtifactInfo>): Promise<Void>? {

    val text: String = trimDummy(coordinates.xmlTag?.value?.text)
    val splitted = text.split(':')
    val searchParameters = createSearchParameters(parameters)
    if (splitted.size < 2) {
      return findPluginByArtifactId(service, text, searchParameters, consumer)
    }
    return service.suggestPrefix(splitted[0], splitted[1], createSearchParameters(parameters), consumer)
  }


  companion object {
    @JvmStatic
    fun findPluginByArtifactId(service: DependencySearchService,
                               text: String,
                               searchParameters: SearchParameters,
                               consumer: Consumer<MavenRepositoryArtifactInfo>): AsyncPromise<Void> {
      //todo: read groups from maven settings.xml
      val apachePromise = service.suggestPrefix("org.apache.maven.plugins", text, searchParameters, consumer)
      val codehausPromise = service.suggestPrefix("org.codehaus.mojo", text, searchParameters, consumer)
      val result = AsyncPromise<Void>()
      listOf(apachePromise, codehausPromise).collectResults().onSuccess { result.setResult(null) }.onError { result.setError(it) }
      return result
    }

    @JvmStatic
    fun isPluginOrExtension(coordinates: MavenDomShortArtifactCoordinates): Boolean {
      return coordinates is MavenDomPlugin || coordinates is MavenDomExtension
    }

  }


}
