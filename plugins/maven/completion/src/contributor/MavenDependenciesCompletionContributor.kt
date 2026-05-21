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
    val grouped = mutableMapOf<Pair<String, String>, MutableList<String>>()
    service.suggestCompletions(DependencyCompletionRequest(text, context)).collect { result ->
      grouped.getOrPut(result.groupId to result.artifactId) { mutableListOf() }.add(result.version)
    }
    for ((coords, versions) in grouped) {
      consumer(MavenRepoArtifactInfo(coords.first, coords.second, versions))
    }
  }
}
