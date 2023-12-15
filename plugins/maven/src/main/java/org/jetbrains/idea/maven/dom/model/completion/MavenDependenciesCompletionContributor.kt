// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.MavenCoordinateCompletionContributor.Companion.trimDummy
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import java.util.function.Consumer


class MavenDependenciesCompletionContributor : MavenCoordinateCompletionAsyncContributor("dependency") {

  override suspend fun find(service: DependencySearchService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            parameters: CompletionParameters,
                            consumer: Consumer<RepositoryArtifactData>) {

    val text: String = trimDummy(coordinates.xmlTag?.value?.text)
    val splitted = text.split(':')
    if (splitted.size < 2) {
      return service.fulltextSearchAsync(text, createSearchParameters(parameters), consumer)
    }
    return service.suggestPrefixAsync(splitted[0], splitted[1], createSearchParameters(parameters), consumer)
  }
}
