// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.api.CodeViewService
import circlet.client.api.ProjectKey
import circlet.client.codeView
import circlet.code.api.ChangeInReview
import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.chains.AsyncDiffRequestChain
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.ListSelection
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolder
import com.intellij.space.vcs.review.details.*
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.runBlocking
import runtime.reactive.Property
import runtime.reactive.SequentialLifetimes

internal data class SpaceReviewDiffRequestData(
  val diffExtensionLifetimes: SequentialLifetimes,
  val spaceDiffVm: SpaceDiffVm,
  val changesVm: SpaceReviewChangesVm,
  val selectedChangeInReview: ChangeInReview,
  val participantsVm: Property<SpaceReviewParticipantsVm?>
)

internal class SpaceDiffRequestChainBuilder(parentLifetime: Lifetime,
                                            private val project: Project,
                                            val spaceDiffVm: SpaceDiffVm,
                                            val changesVm: SpaceReviewChangesVm) {
  private val chainBuilderLifetimes = SequentialLifetimes(parentLifetime)

  fun getRequestChain(listSelection: ListSelection<out ChangeInReview>): DiffRequestChain {
    val lifetime = chainBuilderLifetimes.next()

    return object : AsyncDiffRequestChain() {
      override fun loadRequestProducers(): ListSelection<out DiffRequestProducer> {
        return listSelection.map { changeInReview: ChangeInReview ->
          getDiffRequestProducer(lifetime, changeInReview)
        }
      }

      private fun getDiffRequestProducer(lifetime: Lifetime, change: ChangeInReview): DiffRequestProducer {
        return SpaceDiffRequestProducer(project, lifetime, spaceDiffVm, changesVm, change)
      }
    }
  }
}

private val coroutineContext = ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()

internal class SpaceDiffRequestProducer(
  private val project: Project,
  private val requestProducerLifetime: Lifetime,
  private val spaceDiffVm: SpaceDiffVm,
  private val changesVm: SpaceReviewChangesVm,
  private val change: ChangeInReview,
) : DiffRequestProducer {
  override fun getName(): String = getFilePath(change).path

  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    val projectKey = spaceDiffVm.projectKey
    val selectedCommitHashes = spaceDiffVm.selectedCommits.value
      .filter { it.commitWithGraph.repositoryName == change.repository }
      .map { it.commitWithGraph.commit.id }

    return if (selectedCommitHashes.isNotEmpty()) {
      createSpaceDiffRequest(spaceDiffVm.client.codeView, projectKey, selectedCommitHashes, changesVm.participantsVm)
    }
    else LoadingDiffRequest("")
  }

  private fun createSpaceDiffRequest(codeViewService: CodeViewService,
                                     projectKey: ProjectKey,
                                     selectedCommitHashes: List<String>,
                                     participantsVm: Property<SpaceReviewParticipantsVm?>): SimpleDiffRequest {
    return runBlocking(requestProducerLifetime, coroutineContext) {
      val sideBySideDiff = codeViewService.getSideBySideDiff(projectKey,
                                                             change.repository,
                                                             change.change, false, selectedCommitHashes)

      val leftFileText = getFileContent(sideBySideDiff.left)
      val rightFileText = getFileContent(sideBySideDiff.right)

      val (oldFilePath, newFilePath) = getChangeFilePathInfo(change)
      val diffContentFactory = DiffContentFactoryImpl.getInstanceEx()
      val titles = listOf(change.change.old?.commit, change.change.new?.commit)
      val documents = listOf(
        oldFilePath?.let { diffContentFactory.create(project, leftFileText, it) } ?: diffContentFactory.createEmpty(),
        newFilePath?.let { diffContentFactory.create(project, rightFileText, it) } ?: diffContentFactory.createEmpty()
      )

      val diffRequestData = SpaceReviewDiffRequestData(SequentialLifetimes(requestProducerLifetime),
                                                       spaceDiffVm,
                                                       changesVm,
                                                       change,
                                                       participantsVm)

      return@runBlocking SpaceReviewDiffRequest(getFilePath(change).toString(), documents, titles, requestProducerLifetime, diffRequestData)
    }
  }
}

internal class SpaceReviewDiffRequest(
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