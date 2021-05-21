// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.api.ProjectKey
import com.intellij.diff.chains.AsyncDiffRequestChain
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.ListSelection
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolder
import com.intellij.space.vcs.review.details.ChangesWithDiscussion
import com.intellij.space.vcs.review.details.SpaceReviewChange
import com.intellij.space.vcs.review.details.SpaceReviewParticipantsVm
import libraries.coroutines.extra.Lifetime
import runtime.reactive.LoadingProperty
import runtime.reactive.SequentialLifetimes

internal data class SpaceReviewDiffRequestData(
  val diffExtensionLifetimes: SequentialLifetimes,
  val spaceDiffVm: SpaceDiffVm,
  val changes: LoadingProperty<Map<String, ChangesWithDiscussion>?>,
  val selectedChange: SpaceReviewChange,
  val participantsVm: SpaceReviewParticipantsVm?
)

internal class SpaceDiffRequestChainBuilder(parentLifetime: Lifetime,
                                            private val project: Project,
                                            val spaceDiffVm: SpaceDiffVm) {
  private val chainBuilderLifetimes = SequentialLifetimes(parentLifetime)

  fun getRequestChain(listSelection: ListSelection<SpaceReviewChange>): DiffRequestChain {
    val lifetime = chainBuilderLifetimes.next()

    return object : AsyncDiffRequestChain() {
      override fun loadRequestProducers(): ListSelection<out DiffRequestProducer> {
        return listSelection.map { spaceReviewChange: SpaceReviewChange ->
          getDiffRequestProducer(lifetime, spaceReviewChange)
        }
      }

      private fun getDiffRequestProducer(lifetime: Lifetime, spaceReviewChange: SpaceReviewChange): DiffRequestProducer {
        return SpaceDiffRequestProducer(project, lifetime, spaceDiffVm, spaceDiffVm.changesVm.value.changes, spaceReviewChange)
      }
    }
  }
}

private class SpaceDiffRequestProducer(
  private val project: Project,
  private val requestProducerLifetime: Lifetime,
  private val spaceDiffVm: SpaceDiffVm,
  private val changes: LoadingProperty<Map<String, ChangesWithDiscussion>?>,
  private val spaceReviewChange: SpaceReviewChange,
) : DiffRequestProducer {
  override fun getName(): String = spaceReviewChange.filePath.path
  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    val projectKey = spaceDiffVm.projectKey
    val selectedCommitHashes = spaceDiffVm.changesVm.value.selectedCommits.value
      .filter { it.commitWithGraph.repositoryName == spaceReviewChange.repository }
      .map { it.commitWithGraph.commit.id }

    return if (selectedCommitHashes.isNotEmpty()) {
      createSpaceDiffRequest(projectKey, selectedCommitHashes, changes)
    }
    else LoadingDiffRequest("")
  }

  private fun createSpaceDiffRequest(projectKey: ProjectKey,
                                     selectedCommitHashes: List<String>,
                                     changes: LoadingProperty<Map<String, ChangesWithDiscussion>?>): SimpleDiffRequest {
    val diffData = spaceDiffVm.spaceReviewDiffLoader.loadDiffData(project, projectKey, spaceReviewChange, selectedCommitHashes)

    val diffRequestData = SpaceReviewDiffRequestData(SequentialLifetimes(requestProducerLifetime),
                                                     spaceDiffVm,
                                                     changes,
                                                     spaceReviewChange,
                                                     spaceDiffVm.participantVm.value)

    return SpaceReviewDiffRequest(spaceReviewChange.filePath.path, diffData.contents, diffData.titles, requestProducerLifetime,
                                  diffRequestData)
  }
}

private class SpaceReviewDiffRequest(
  @NlsSafe dialogTitle: String,
  diffContents: List<DiffContent>,
  titles: List<String?>,
  private val requestProducerLifetime: Lifetime,
  private val requestData: SpaceReviewDiffRequestData
) : SimpleDiffRequest(dialogTitle, diffContents, titles) {

  init {
    putUserData(SpaceDiffKeys.DIFF_REQUEST_DATA, requestData)
  }

  override fun onAssigned(isAssigned: Boolean) {
    // in case when diff created in windowed mode it is impossible to terminate top-level lifetime on window dispose
    // to make sure that all processes running in the comment diff extension will terminate correctly we need this hack, because
    // this code will also be called when the diff window is disposed
    if (!isAssigned && !requestProducerLifetime.isTerminated) requestData.diffExtensionLifetimes.next()
    super.onAssigned(isAssigned)
  }
}