// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom.model.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.dom.model.completion.insert.MavenArtifactIdInsertionHandler
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.reposearch.DependencySearchService
import org.jetbrains.idea.reposearch.RepositoryArtifactData
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.function.Consumer
import java.util.function.Predicate

class MavenArtifactIdCompletionContributor : MavenCoordinateCompletionContributor("artifactId") {

  override fun validate(groupId: String, artifactId: String): Boolean {
    return true
  }

  override fun find(service: DependencySearchService,
                    coordinates: MavenDomShortArtifactCoordinates,
                    parameters: CompletionParameters,
                    consumer: Consumer<RepositoryArtifactData>): Promise<Int> {

    val searchParameters = createSearchParameters(parameters)
    val groupId = trimDummy(coordinates.groupId.stringValue)
    val artifactId = trimDummy(coordinates.artifactId.stringValue)
    if (MavenAbstractPluginExtensionCompletionContributor.isPluginOrExtension(coordinates) && StringUtil.isEmpty(groupId)) {
      return MavenAbstractPluginExtensionCompletionContributor.findPluginByArtifactId(service, artifactId, searchParameters, consumer)
    }
    return service.suggestPrefix(groupId, artifactId, searchParameters,
                                 withPredicate(consumer,
                                               Predicate { it is MavenRepositoryArtifactInfo && (groupId.isEmpty() || groupId == it.groupId) }))
  }

  override fun fillResults(result: CompletionResultSet,
                           coordinates: MavenDomShortArtifactCoordinates,
                           cld: ConcurrentLinkedDeque<RepositoryArtifactData>,
                           promise: Promise<Int>) {
    val set = HashSet<String>()
    //todo hot spot - spin loop (see also parent and other child classes)
    while (promise.state == Promise.State.PENDING || !cld.isEmpty()) {
      ProgressManager.checkCanceled()
      val item = cld.poll()
      if (item is MavenRepositoryArtifactInfo && set.add(item.artifactId)) {
        result
          .addElement(
            MavenDependencyCompletionUtil.lookupElement(item, item.artifactId).withInsertHandler(
              MavenArtifactIdInsertionHandler.INSTANCE))
      }
    }
  }
}
