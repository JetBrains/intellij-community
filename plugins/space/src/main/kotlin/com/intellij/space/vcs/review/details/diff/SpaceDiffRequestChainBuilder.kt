// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.codeView
import circlet.code.api.ChangeInReview
import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.chains.AsyncDiffRequestChain
import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.ListSelection
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.space.vcs.review.details.*
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.runBlocking
import runtime.reactive.Property
import runtime.reactive.SequentialLifetimes

internal data class DiffRequestData(
  val lifetime: SequentialLifetimes,
  val spaceDiffVm: SpaceDiffVm,
  val changesVm: SpaceReviewChangesVm,
  val selectedChangeInReview: ChangeInReview,
  val participantsVm: Property<SpaceReviewParticipantsVm?>
)

internal class SpaceDiffRequestChainBuilder(editorLifeTime: Lifetime,
                                            private val project: Project,
                                            private val spaceDiffFile: SpaceDiffFile) {
  private val sequentialLifetimes = SequentialLifetimes(editorLifeTime)

  fun getRequestChain(listSelection: ListSelection<out ChangeInReview>): DiffRequestChain {
    val spaceDiffVm = spaceDiffFile.diffVm
    val changesVm = spaceDiffFile.changesVm

    val lifetime = sequentialLifetimes.next()

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
  private val lifetime: Lifetime,
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

    if (selectedCommitHashes.isNotEmpty()) {
      val participantsVm = changesVm.participantsVm
      val codeViewService = spaceDiffVm.client.codeView

      return runBlocking(lifetime, coroutineContext) {
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

        val diffRequestData = DiffRequestData(SequentialLifetimes(lifetime), spaceDiffVm, changesVm, change, participantsVm)

        return@runBlocking SimpleDiffRequest(getFilePath(change).toString(), documents, titles).apply {
          putUserData(SpaceDiffKeys.DIFF_REQUEST_DATA, diffRequestData)
        }
      }
    }
    else return LoadingDiffRequest("")
  }
}