// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import org.jetbrains.idea.maven.completion.MavenDependencySearchService
import org.jetbrains.idea.maven.dom.model.MavenDomExtension
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import java.util.function.Consumer


abstract class MavenAbstractPluginExtensionCompletionContributor(tagName: String) : MavenCoordinateCompletionContributor(tagName) {
    override suspend fun find(service: MavenDependencySearchService,
                              coordinates: MavenDomShortArtifactCoordinates,
                              parameters: CompletionParameters,
                              consumer: (MavenRepoArtifactInfo) -> Unit) {

    val text: String = trimDummy(coordinates.xmlTag?.value?.text)
    val splitted = text.split(':')
    val (useCache, useLocalOnly) = createSearchParameters(parameters)
    if (splitted.size < 2) {
      return findPluginByArtifactId(service, text, useCache, useLocalOnly, consumer)
    }
    return service.suggestPrefix(splitted[0], splitted[1], useCache, useLocalOnly, consumer)
  }


  companion object {
    @JvmStatic
    suspend fun findPluginByArtifactId(service: MavenDependencySearchService,
                                       text: String,
                                       useCache: Boolean,
                                       useLocalOnly: Boolean,
                                       consumer: Consumer<MavenRepoArtifactInfo>) {
      //todo: read groups from maven settings.xml
      service.suggestPrefix("org.apache.maven.plugins", text, useCache, useLocalOnly, consumer)
      service.suggestPrefix("org.codehaus.mojo", text, useCache, useLocalOnly, consumer)
    }

    @JvmStatic
    fun isPluginOrExtension(coordinates: MavenDomShortArtifactCoordinates): Boolean {
      return coordinates is MavenDomPlugin || coordinates is MavenDomExtension
    }

  }


}
