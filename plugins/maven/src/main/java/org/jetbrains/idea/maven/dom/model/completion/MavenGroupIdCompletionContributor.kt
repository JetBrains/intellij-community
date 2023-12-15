// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenDependencyInsertionHandler
import org.jetbrains.idea.maven.indices.IndicesBundle
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.idea.reposearch.PoisonedRepositoryArtifactData
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Consumer
import java.util.function.Predicate

class MavenGroupIdCompletionContributor : MavenCoordinateCompletionContributor("groupId") {

  override fun handleEmptyLookup(parameters: CompletionParameters, editor: Editor): @NlsContexts.HintText String? {
    return if (isCorrectPlace(parameters)) {
      IndicesBundle.message("maven.dependency.completion.group.empty")
    }
    else null
  }

  override fun find(service: DependencySearchService,
                    coordinates: MavenDomShortArtifactCoordinates,
                    parameters: CompletionParameters,
                    consumer: Consumer<RepositoryArtifactData>): Promise<Int> {
    val searchParameters = createSearchParameters(parameters)
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    return service.suggestPrefix(groupId, artifactId, searchParameters, withPredicate(consumer,
                                                                                      Predicate { it is MavenRepositoryArtifactInfo && (artifactId.isEmpty() || artifactId == it.artifactId) }))
  }

  override fun fillResults(result: CompletionResultSet,
                           coordinates: MavenDomShortArtifactCoordinates,
                           cld: ConcurrentLinkedDeque<RepositoryArtifactData>,
                           promise: Promise<Int>,
                           completionPrefix: String) {
    val set = HashSet<String>()
    while (promise.state == Promise.State.PENDING || !cld.isEmpty()) {
      ProgressManager.checkCanceled()
      val item = cld.poll()
      if (item is MavenRepositoryArtifactInfo && set.add(item.groupId)) {
        result.addElement(
          MavenDependencyCompletionUtil.lookupElement(item, item.groupId)
            .withInsertHandler(MavenDependencyInsertionHandler.INSTANCE)
            .also { it.putUserData(MAVEN_COORDINATE_COMPLETION_PREFIX_KEY, completionPrefix) }
        )
      }
      if (item === PoisonedRepositoryArtifactData.INSTANCE) break
    }
  }
}
