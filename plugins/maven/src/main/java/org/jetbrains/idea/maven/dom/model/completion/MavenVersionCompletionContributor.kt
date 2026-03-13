// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.idea.maven.completion.MavenDependencySearchService
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.MavenAbstractPluginExtensionCompletionContributor.Companion.findPluginByArtifactId
import org.jetbrains.idea.maven.dom.model.completion.MavenAbstractPluginExtensionCompletionContributor.Companion.isPluginOrExtension
import org.jetbrains.idea.maven.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription
import java.util.function.Consumer

open class MavenVersionCompletionContributor : MavenCoordinateCompletionContributor("version") {
  override suspend fun find(service: MavenDependencySearchService,
                            coordinates: MavenDomShortArtifactCoordinates,
                            parameters: CompletionParameters,
                            consumer: (MavenRepoArtifactInfo) -> Unit) {
    val (useCache, useLocalOnly) = createSearchParameters(parameters)
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)

    val artifactDataConsumer = RepositoryArtifactDataConsumer(artifactId, groupId, consumer)
    if (isPluginOrExtension(coordinates) && groupId.isEmpty()) {
      return findPluginByArtifactId(service, artifactId, useCache, useLocalOnly, artifactDataConsumer)
    }

    return service.suggestPrefix(groupId, artifactId, useCache, useLocalOnly, artifactDataConsumer)
  }

  override fun fillAfter(result: CompletionResultSet) {
    if (MavenServerManager.getInstance().isUseMaven2) {
      result.addElement(LookupElementBuilder.create(RepositoryLibraryDescription.ReleaseVersionId).withStrikeoutness(true))
      result.addElement(LookupElementBuilder.create(RepositoryLibraryDescription.LatestVersionId).withStrikeoutness(true))
    }
  }

  override fun fillResult(coordinates: MavenDomShortArtifactCoordinates,
                          result: CompletionResultSet,
                          item: MavenRepoArtifactInfo,
                          completionPrefix: String) {
    result.addAllElements(
      item.items.map { dci: MavenDependencyCompletionItem ->
        val version = dci.version ?: return
        val lookup = MavenDependencyCompletionUtil.lookupElement(dci, version)
        lookup.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix)
        lookup
      }
    )
  }

  override fun amendResultSet(result: CompletionResultSet): CompletionResultSet {
    return result.withRelevanceSorter(CompletionService.getCompletionService().emptySorter().weigh(MavenVersionNegatingWeigher()))
  }

  private class RepositoryArtifactDataConsumer(private val myArtifactId: String,
                                               private val myGroupId: String,
                                               private val myConsumer: Consumer<MavenRepoArtifactInfo>) : Consumer<MavenRepoArtifactInfo> {
    override fun accept(rad: MavenRepoArtifactInfo) {
      if (rad.artifactId == myArtifactId &&
          (myGroupId.isEmpty() || rad.groupId == myGroupId)) {
        myConsumer.accept(rad)
      }
    }
  }
}