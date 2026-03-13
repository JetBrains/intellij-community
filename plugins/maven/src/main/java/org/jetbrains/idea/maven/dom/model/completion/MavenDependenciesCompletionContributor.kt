// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import org.jetbrains.idea.maven.completion.MavenDependencySearchService
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo


class MavenDependenciesCompletionContributor : MavenCoordinateCompletionContributor("dependency") {

  override suspend fun find(service: MavenDependencySearchService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            parameters: CompletionParameters,
                            consumer: (MavenRepoArtifactInfo) -> Unit) {

    val text: String = trimDummy(coordinates.xmlTag?.value?.text)
    val splitted = text.split(':')
    val (useCache, useLocalOnly) = createSearchParameters(parameters)
    if (splitted.size < 2) {
      return service.fulltextSearch(text, useCache, useLocalOnly, consumer)
    }
    return service.suggestPrefix(splitted[0], splitted[1], useCache, useLocalOnly, consumer)
  }
}
