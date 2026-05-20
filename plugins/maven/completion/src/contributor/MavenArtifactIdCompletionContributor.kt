// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionService
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenArtifactIdInsertionHandler
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo

class MavenArtifactIdCompletionContributor : MavenCoordinateCompletionContributor("artifactId") {

  override suspend fun find(service: DependencyCompletionService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            context: DependencyCompletionContext,
                            consumer: (MavenRepoArtifactInfo) -> Unit) {
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    if (MavenAbstractPluginExtensionCompletionContributor.isPluginOrExtension(coordinates) && groupId.isEmpty()) {
      MavenAbstractPluginExtensionCompletionContributor.findArtifactsInPluginGroups(service, artifactId, context, consumer)
      return
    }
    service.suggestArtifactCompletions(DependencyArtifactCompletionRequest(groupId, artifactId, context)).collect { result ->
      consumer(MavenRepoArtifactInfo(groupId, result.result, emptyList()))
    }
  }

  override fun fillResults(result: CompletionResultSet,
                           coordinates: MavenDomShortArtifactCoordinates,
                           item: MavenRepoArtifactInfo,
                           completionPrefix: String) {
    result.addElement(
      MavenDependencyCompletionUtil.lookupElement(item, item.artifactId)
        .withInsertHandler(MavenArtifactIdInsertionHandler.INSTANCE)
        .also { it.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix) }
    )
  }

  override fun resultFilter(): MavenCoordinateCompletionResultFilter =
    MavenCoordinateCompletionResultFilter.uniqueProperty { it.artifactId }
}
