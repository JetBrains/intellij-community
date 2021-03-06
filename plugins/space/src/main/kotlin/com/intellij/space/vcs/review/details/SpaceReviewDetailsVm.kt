// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.ProjectKey
import circlet.client.api.TD_MemberProfile
import circlet.client.api.identifier
import circlet.code.api.*
import circlet.code.codeReview
import circlet.platform.api.Ref
import circlet.platform.api.TID
import circlet.platform.client.*
import circlet.workspaces.Workspace
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.space.editor.SpaceVirtualFilesManager
import com.intellij.space.utils.SpaceUrls
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.details.diff.SpaceDiffVm
import com.intellij.space.vcs.review.details.diff.SpaceDiffVmImpl
import com.intellij.space.vcs.review.details.diff.load.SpaceLocalDiffLoaderWithCallback
import com.intellij.space.vcs.review.details.process.SpaceReviewStateUpdater
import com.intellij.space.vcs.review.details.process.SpaceReviewStateUpdaterImpl
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.GitLocalBranch
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.*
import runtime.reactive.property.map
import runtime.reactive.property.mapInit

internal sealed class SpaceReviewDetailsVm<R : CodeReviewRecord>(
  final override val lifetime: Lifetime,
  val ideaProject: Project,
  val spaceProjectInfo: SpaceProjectInfo,
  val spaceReposInfo: Set<SpaceRepoInfo>,
  val reviewRef: Ref<R>,
  val workspace: Workspace
) : Lifetimed {
  val client: KCircletClient = workspace.client

  val review: Property<R> = reviewRef.property()

  val projectKey: ProjectKey = review.value.project
  val reviewKey: String? = review.value.key

  val reviewUrl: String = SpaceUrls.review(projectKey, review.value.number)

  val reviewId: TID = review.value.id

  val title: Property<String> = cellProperty { review.live.title }

  val state: Property<CodeReviewState> = cellProperty { review.live.state }

  val createdAt: Property<Long> = cellProperty { review.live.createdAt }

  val createdBy: Property<TD_MemberProfile?> = cellProperty { review.live.createdBy?.resolve() }

  val turnBased: Property<Boolean?> = cellProperty { review.live.turnBased }

  val reviewStateUpdater: SpaceReviewStateUpdater = SpaceReviewStateUpdaterImpl(workspace, review.value)

  val infoByRepos = spaceReposInfo.associateBy(SpaceRepoInfo::name)

  private val participantsProperty: Property<LoadingValue<Ref<CodeReviewParticipants>>> = load {
    client.arena.resolveRefsOrFetch {
      reviewRef.extensionRef(CodeReviewParticipants::class)
    }
  }

  private val participantsRef: Property<Ref<CodeReviewParticipants>?> = lastLoadedValueOrNull(participantsProperty)
  private val pendingCounterRef: Property<Ref<CodeReviewPendingMessageCounter>?> = lastLoadedValueOrNull(pendingCounterAsync(client))

  val participantsVm: Property<SpaceReviewParticipantsVm?> = map(participantsRef, pendingCounterRef) { participantsRef, pendingCounterRef ->
    if (participantsRef != null && pendingCounterRef != null) {
      SpaceReviewParticipantsVmImpl(lifetime, projectKey, reviewRef, participantsRef, pendingCounterRef, review.value.identifier, workspace)
    }
    else null
  }

  private val detailedInfo: Property<CodeReviewDetailedInfo?> = mapInit(review, null) {
    client.codeReview.getReviewDetails(review.value.project.identifier, review.value.identifier)
  }

  val commits: Property<List<SpaceReviewCommitListItem>> = mapInit(detailedInfo, emptyList()) { detailedInfo ->
    val revisions: List<RevisionsInReview> = detailedInfo?.commits ?: emptyList()

    revisions.flatMap { revInReview ->
      val repo = revInReview.repository
      val repoInfo = infoByRepos[repo.name]
      val commitsInRepository = revInReview.commits.size

      revInReview.commits
        .filterNot { this@SpaceReviewDetailsVm is MergeRequestDetailsVm && it.unreachable }
        .mapIndexed { index, gitCommitWithGraph ->
          SpaceReviewCommitListItem(gitCommitWithGraph, repo, index, commitsInRepository, repoInfo)
        }
    }
  }

  val selectedTab: MutableProperty<SelectedTab> = mutableProperty(SelectedTab.INFO)

  private val allReachableCommits: Property<List<SpaceReviewCommitListItem>> = map(commits) { commits ->
    commits.filterNot { it.commitWithGraph.unreachable }
  }

  val commitChangesVm: SpaceReviewChangesVm = SpaceReviewChangesVmImpl(
    lifetime, client, projectKey, review.value.identifier,
    reviewId, commits, participantsVm, infoByRepos
  )

  val allChangesVm: SpaceReviewChangesVmImpl = SpaceReviewChangesVmImpl(
    lifetime, client, projectKey, review.value.identifier,
    reviewId, allReachableCommits, participantsVm, infoByRepos
  )

  val selectedChangesVm: Property<SpaceReviewChangesVm> = map(selectedTab) { tab ->
    selectedOrAll(tab, commitChangesVm, allChangesVm)
  }

  val spaceDiffVm: SpaceDiffVm = SpaceDiffVmImpl(client,
                                                 reviewId,
                                                 reviewKey as String,
                                                 projectKey,
                                                 selectedChangesVm,
                                                 SpaceLocalDiffLoaderWithCallback(lifetime, client),
                                                 participantsVm).also {
   ideaProject.service<SpaceVirtualFilesManager>()
      .updateDiffPresentation(selectedChangesVm, it)
  }
}

private fun <T> selectedOrAll(tab: SelectedTab, selected: T, all: T): T = when (tab) {
  SelectedTab.INFO -> all
  SelectedTab.COMMITS -> selected
}

internal class MergeRequestDetailsVm(
  parentDisposable: Disposable,
  lifetime: Lifetime,
  ideaProject: Project,
  spaceProjectInfo: SpaceProjectInfo,
  spaceReposInfo: Set<SpaceRepoInfo>,
  refMrRecord: Ref<MergeRequestRecord>,
  workspace: Workspace
) : SpaceReviewDetailsVm<MergeRequestRecord>(lifetime, ideaProject, spaceProjectInfo, spaceReposInfo, refMrRecord, workspace) {

  val branchPair: Property<MergeRequestBranchPair> = cellProperty { review.live.branchPair }

  val repository: Property<String> = cellProperty { branchPair.live.repository }
  val targetBranchInfo: Property<MergeRequestBranch?> = cellProperty { branchPair.live.targetBranchInfo }
  val sourceBranchInfo: Property<MergeRequestBranch?> = cellProperty { branchPair.live.sourceBranchInfo }

  val repoInfo: Property<SpaceRepoInfo?> = map(repository) { repoName ->
    getRepoInfo(repoName)
  }

  val mergeRequestBranchInfo: MutableProperty<MergeRequestBranchInfo> = mutableProperty(MergeRequestBranchInfo(null, false))

  private fun getRepoInfo(repoName: String): SpaceRepoInfo? {
    return spaceReposInfo.firstOrNull { it.name == repoName }
  }

  init {
    review.forEach(lifetime) {
      updateLocalBranch()
    }

    val dataPackListener = DataPackChangeListener {
      updateLocalBranch()
    }

    VcsProjectLog.runWhenLogIsReady(ideaProject) {
      val log = VcsProjectLog.getInstance(ideaProject)
      val dataManager = log.dataManager ?: return@runWhenLogIsReady
      ApplicationManager.getApplication().invokeLater(
        {
          dataManager.addDataPackChangeListener(dataPackListener)
          Disposer.register(parentDisposable, Disposable { dataManager.removeDataPackChangeListener(dataPackListener) })
        },
        {
          Disposer.isDisposed(parentDisposable)
        }
      )
    }
  }

  private fun updateLocalBranch() {
    val localBranch = kotlin.run {
      val repoInfo = repoInfo.value ?: return@run null
      val branch = sourceBranchInfo.value ?: return@run null
      findLocalBranch(repoInfo, branch)
    }

    val isCurrentBranch = kotlin.run {
      val gitLocalBranch = localBranch ?: return@run false
      val spaceRepoInfo = repoInfo.value ?: return@run false
      return@run spaceRepoInfo.repository.currentBranch == gitLocalBranch
    }

    mergeRequestBranchInfo.value = MergeRequestBranchInfo(localBranch, isCurrentBranch)
  }

  private fun findLocalBranch(repoInfo: SpaceRepoInfo, branch: MergeRequestBranch): GitLocalBranch? {
    val gitRepository = repoInfo.repository
    val allBranches = gitRepository.branches

    val remoteBranch = allBranches.remoteBranches
      .find { remoteBranch -> remoteBranch.nameForRemoteOperations == branch.displayName } ?: return null

    return allBranches.localBranches
      .firstOrNull { it.findTrackedBranch(gitRepository) == remoteBranch }
  }
}

internal class CommitSetReviewDetailsVm(
  lifetime: Lifetime,
  ideaProject: Project,
  spaceProjectInfo: SpaceProjectInfo,
  spaceReposInfo: Set<SpaceRepoInfo>,
  refMrRecord: Ref<CommitSetReviewRecord>,
  workspace: Workspace
) : SpaceReviewDetailsVm<CommitSetReviewRecord>(lifetime, ideaProject, spaceProjectInfo, spaceReposInfo, refMrRecord, workspace)

internal fun createReviewDetailsVm(disposable: Disposable,
                                   lifetime: Lifetime,
                                   project: Project,
                                   workspace: Workspace,
                                   spaceProjectInfo: SpaceProjectInfo,
                                   spaceReposInfo: Set<SpaceRepoInfo>,
                                   codeReviewListItem: CodeReviewListItem): SpaceReviewDetailsVm<out CodeReviewRecord> {
  val client = workspace.client
  return when (val codeReviewRecord = codeReviewListItem.review.resolve()) {
    is MergeRequestRecord -> MergeRequestDetailsVm(
      disposable,
      lifetime,
      project,
      spaceProjectInfo,
      spaceReposInfo,
      codeReviewRecord.toRef(client.arena),
      workspace
    )
    is CommitSetReviewRecord -> CommitSetReviewDetailsVm(
      lifetime,
      project,
      spaceProjectInfo,
      spaceReposInfo,
      codeReviewRecord.toRef(client.arena),
      workspace
    )
    else -> throw IllegalArgumentException("Unable to resolve CodeReviewRecord")
  }
}

enum class SelectedTab {
  INFO,
  COMMITS
}

private fun SpaceReviewDetailsVm<*>.pendingCounterAsync(client: KCircletClient): LoadingProperty<Ref<CodeReviewPendingMessageCounter>> {
  return load {
    client.arena.resolveRefsOrFetch {
      reviewRef.extensionRef(CodeReviewPendingMessageCounter::class)
    }
  }
}

internal class MergeRequestBranchInfo(
  val localBranch: GitLocalBranch?,
  val isCurrentBranch: Boolean
)

internal fun <R : CodeReviewRecord> SpaceReviewDetailsVm<R>.isMergeRequest(): Boolean {
  return this is MergeRequestDetailsVm
}

internal fun <R : CodeReviewRecord> SpaceReviewDetailsVm<R>.isCommitSetReview(): Boolean {
  return this is CommitSetReviewDetailsVm
}