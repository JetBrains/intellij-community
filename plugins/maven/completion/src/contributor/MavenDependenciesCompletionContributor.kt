// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion.contributor

import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo


class MavenDependenciesCompletionContributor : MavenCoordinateCompletionContributor("dependency") {

  override suspend fun find(service: DependencyCompletionService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            context: DependencyCompletionContext,
                            consumer: (MavenRepoArtifactInfo) -> Unit) {
    val text = trimDummy(coordinates.xmlTag?.value?.text)
    service.suggestCompletions(DependencyCompletionRequest(text, context)).collect { result ->
      consumer(MavenRepoArtifactInfo(result.groupId, result.artifactId, listOf(result.version)))
    }
  }
}
