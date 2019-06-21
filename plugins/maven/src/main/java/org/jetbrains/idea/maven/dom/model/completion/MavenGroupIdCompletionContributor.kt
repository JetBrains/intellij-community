// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.openapi.editor.Editor
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.indices.IndicesBundle
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import java.util.function.Consumer
import java.util.function.Predicate

class MavenGroupIdCompletionContributor : MavenCoordinateCompletionContributor("groupId") {

  override fun handleEmptyLookup(parameters: CompletionParameters, editor: Editor): String? {
    return if (PlaceChecker(parameters).checkPlace().isCorrectPlace()) {
      IndicesBundle.message("maven.dependency.completion.group.empty")
    }
    else null
  }

  override fun find(service: DependencySearchService,
                    coordinates: MavenDomShortArtifactCoordinates,
                    parameters: CompletionParameters,
                    consumer: Consumer<MavenRepositoryArtifactInfo>): Promise<Void> {
    val searchParameters = createSearchParameters(parameters)
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    return service.suggestPrefix(groupId, artifactId, searchParameters, withPredicate(consumer,
                                                                           Predicate { searchParameters.isShowAll || artifactId.isEmpty() || artifactId == it.artifactId}))
  }


}
