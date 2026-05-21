// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsContexts
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionHandler
import org.jetbrains.idea.maven.indices.IndicesBundle
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo

class MavenGroupIdCompletionContributor : MavenCoordinateCompletionContributor("groupId") {

  override fun handleEmptyLookup(parameters: CompletionParameters, editor: Editor): @NlsContexts.HintText String? {
    return if (isCorrectPlace(parameters)) {
      IndicesBundle.message("maven.dependency.completion.group.empty")
    }
    else null
  }

  override suspend fun find(service: DependencyCompletionService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            context: DependencyCompletionContext,
                            consumer: (MavenRepoArtifactInfo) -> Unit) {
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    val grouped = mutableMapOf<String, MutableList<String>>()
    service.suggestCompletions(DependencyCompletionRequest(groupId, context)).collect { result ->
      if (artifactId.isEmpty() || result.artifactId == artifactId) {
        grouped.getOrPut(result.groupId) { mutableListOf() }.add(result.version)
      }
    }
    for ((grp, versions) in grouped) {
      consumer(MavenRepoArtifactInfo(grp, artifactId, versions))
    }
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

  override fun resultFilter(): MavenCoordinateCompletionResultFilter =
    MavenCoordinateCompletionResultFilter.uniqueProperty { it.groupId }
}
