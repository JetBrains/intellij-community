/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.MavenAbstractPluginExtensionCompletionContributor.Companion.findPluginByArtifactId
import org.jetbrains.idea.maven.dom.model.completion.MavenAbstractPluginExtensionCompletionContributor.Companion.isPluginOrExtension
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import java.util.function.Consumer

open class MavenVersionCompletionContributor : MavenCoordinateCompletionContributor("version") {
    override suspend fun find(service: DependencySearchService,
                              coordinates: MavenDomShortArtifactCoordinates,
                              parameters: CompletionParameters,
                              consumer: (RepositoryArtifactData) -> Unit) {
    val searchParameters = createSearchParameters(parameters)
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)

    val artifactDataConsumer = RepositoryArtifactDataConsumer(artifactId, groupId, consumer)
    if (isPluginOrExtension(coordinates) && groupId.isEmpty()) {
      return findPluginByArtifactId(service, artifactId, searchParameters, artifactDataConsumer)
    }

    return service.suggestPrefixAsync(groupId, artifactId, searchParameters, artifactDataConsumer)
  }

  override fun fillAfter(result: CompletionResultSet) {
    if (MavenServerManager.getInstance().isUseMaven2) {
      result.addElement(LookupElementBuilder.create(RepositoryLibraryDescription.ReleaseVersionId).withStrikeoutness(true))
      result.addElement(LookupElementBuilder.create(RepositoryLibraryDescription.LatestVersionId).withStrikeoutness(true))
    }
  }

  override fun fillResult(coordinates: MavenDomShortArtifactCoordinates,
                          result: CompletionResultSet,
                          item: MavenRepositoryArtifactInfo,
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
                                               private val myConsumer: Consumer<RepositoryArtifactData>) : Consumer<RepositoryArtifactData> {
    override fun accept(rad: RepositoryArtifactData) {
      if (rad is MavenRepositoryArtifactInfo) {
        if (rad.artifactId == myArtifactId &&
            (myGroupId.isEmpty() || rad.groupId == myGroupId)) {
          myConsumer.accept(rad)
        }
      }
    }
  }
}
