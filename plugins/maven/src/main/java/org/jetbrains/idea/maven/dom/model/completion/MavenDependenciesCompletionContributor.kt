// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import java.util.function.Consumer


class MavenDependenciesCompletionContributor : MavenCoordinateCompletionContributor("dependency") {

  override fun find(service: DependencySearchService,
                    coordinates: MavenDomShortArtifactCoordinates,
                    parameters: CompletionParameters,
                    consumer: Consumer<RepositoryArtifactData>): Promise<Int> {

    val text: String = trimDummy(coordinates.xmlTag?.value?.text)
    val splitted = text.split(':')
    if (splitted.size < 2) {
      return service.fulltextSearch(text, createSearchParameters(parameters), consumer)
    }
    return service.suggestPrefix(splitted[0], splitted[1], createSearchParameters(parameters), consumer)
  }
}
