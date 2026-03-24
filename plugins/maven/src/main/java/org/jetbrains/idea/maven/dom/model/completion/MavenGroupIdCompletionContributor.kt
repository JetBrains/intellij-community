// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.idea.maven.completion.MavenDependencySearchService
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionHandler
import org.jetbrains.idea.maven.indices.IndicesBundle
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import java.util.function.Predicate

class MavenGroupIdCompletionContributor : MavenCoordinateCompletionContributor("groupId") {

  override fun handleEmptyLookup(parameters: CompletionParameters, editor: Editor): @NlsContexts.HintText String? {
    return if (isCorrectPlace(parameters)) {
      IndicesBundle.message("maven.dependency.completion.group.empty")
    }
    else null
  }

  override suspend fun find(service: MavenDependencySearchService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            parameters: CompletionParameters,
                            consumer: (MavenRepoArtifactInfo) -> Unit) {
    val (useCache, useLocalOnly) = createSearchParameters(parameters)
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    return service.suggestPrefix(
      groupId, artifactId, useCache, useLocalOnly,
      withPredicate(consumer, Predicate { artifactId.isEmpty() || artifactId == it.artifactId }))
  }

  override fun fillResults(result: CompletionResultSet,
                           coordinates: MavenDomShortArtifactCoordinates,
                           item: MavenRepoArtifactInfo,
                           completionPrefix: String) {
    result.addElement(
      MavenDependencyCompletionUtil.lookupElement(item, item.groupId)
        .withInsertHandler(MavenDependencyInsertionHandler.INSTANCE)
        .also { it.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix) }
    )
  }

  override fun resultFilter(): MavenCoordinateCompletionResultFilter = MavenCoordinateCompletionResultFilter.uniqueProperty { it.groupId }
}
