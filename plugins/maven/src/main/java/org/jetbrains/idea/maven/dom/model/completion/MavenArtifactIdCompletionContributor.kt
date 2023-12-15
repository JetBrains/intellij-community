// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenArtifactIdInsertionHandler
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import java.util.function.Consumer
import java.util.function.Predicate

class MavenArtifactIdCompletionContributor : MavenCoordinateCompletionContributor("artifactId") {

  override suspend fun find(service: DependencySearchService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            parameters: CompletionParameters,
                            consumer: Consumer<RepositoryArtifactData>) {
    val searchParameters = createSearchParameters(parameters)
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    if (MavenAbstractPluginExtensionCompletionContributor.isPluginOrExtension(coordinates) && StringUtil.isEmpty(groupId)) {
      return MavenAbstractPluginExtensionCompletionContributor.findPluginByArtifactId(service, artifactId, searchParameters, consumer)
    }
    return service.suggestPrefixAsync(
      groupId, artifactId, searchParameters,
      withPredicate(consumer, Predicate { it is MavenRepositoryArtifactInfo && (groupId.isEmpty() || groupId == it.groupId) }))
  }

  override fun fillResults(result: CompletionResultSet,
                           coordinates: MavenDomShortArtifactCoordinates,
                           item: RepositoryArtifactData,
                           completionPrefix: String) {
    if (item is MavenRepositoryArtifactInfo) {
      result.addElement(
        MavenDependencyCompletionUtil.lookupElement(item, item.artifactId)
          .withInsertHandler(MavenArtifactIdInsertionHandler.INSTANCE)
          .also { it.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix) }
      )
    }
  }
}