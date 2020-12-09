// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.ProjectKey
import circlet.client.api.TD_MemberProfile
import circlet.client.api.identifier
import circlet.code.api.*
import circlet.code.codeReview
import circlet.platform.api.Ref
import circlet.platform.api.TID
import circlet.platform.client.*
import com.intellij.openapi.ListSelection
import com.intellij.openapi.project.Project
import com.intellij.space.utils.SpaceUrls
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import com.intellij.space.vcs.review.details.diff.SpaceDiffVm
import com.intellij.space.vcs.review.details.diff.SpaceDiffVmImpl
import com.intellij.space.vcs.review.details.diff.SpaceReviewDiffLoader
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.*

internal sealed class SpaceReviewDetailsVm<R : CodeReviewRecord>(
  final override val lifetime: Lifetime,
  val ideaProject: Project,
  val spaceProjectInfo: SpaceProjectInfo,
  spaceReposInfo: Set<SpaceRepoInfo>,
  private val reviewRef: Ref<R>,
  val client: KCircletClient
) : Lifetimed {
  val review: Property<R> = reviewRef.property()

  val projectKey: ProjectKey = review.value.project
  val reviewKey: String? = review.value.key

  val reviewUrl: String = SpaceUrls.review(projectKey, review.value.number)

  val reviewId: TID = review.value.id

  val title: Property<String> = cellProperty { review.live.title }

  val state: Property<CodeReviewState> = cellProperty { review.live.state }

  val createdAt: Property<Long> = cellProperty { review.live.createdAt }

  val createdBy: Property<TD_MemberProfile> = cellProperty { review.live.createdBy!!.resolve() }

  val turnBased: Property<Boolean?> = cellProperty { review.live.turnBased }

  private val infoByRepos = spaceReposInfo.associateBy(SpaceRepoInfo::name)

  private val participantsProperty: Property<LoadingValue<Ref<CodeReviewParticipants>>> = load {
    client.arena.resolveRefsOrFetch {
      reviewRef.extensionRef(CodeReviewParticipants::class)
    }
  }

  private val participantsRef: Property<Ref<CodeReviewParticipants>?> = lastLoadedValueOrNull(participantsProperty)

  val participantsVm: Property<SpaceReviewParticipantsVm?> = seqMap(participantsRef) { r ->
    r?.let { SpaceReviewParticipantsVmImpl(it, projectKey, review.value.identifier, client, lifetime) }
  }

  private val detailedInfo: Property<CodeReviewDetailedInfo?> = mapInit(review, null) {
    client.codeReview.getReviewDetails(review.value.project.identifier, review.value.identifier)
  }

  val commits: Property<List<ReviewCommitListItem>?> = mapInit(detailedInfo, null) { detailedInfo ->
    detailedInfo?.commits?.flatMap { revInReview ->
      val repo = revInReview.repository
      val repoInfo = infoByRepos[repo.name]
      val commitsInRepository = revInReview.commits.size

      revInReview.commits
        .filterNot(GitCommitWithGraph::unreachable)
        .mapIndexed { index, gitCommitWithGraph -> ReviewCommitListItem(gitCommitWithGraph, repo, index, commitsInRepository, repoInfo) }
    }
  }

  val selectedCommitIndices: MutableProperty<List<Int>> = Property.createMutable(emptyList())

  private val selectedCommits: Property<List<ReviewCommitListItem>> = mapInit(selectedCommitIndices, commits,
                                                                              emptyList()) { indices, commits ->
    commits ?: return@mapInit emptyList<ReviewCommitListItem>()

    if (indices.isEmpty()) return@mapInit commits

    indices.map { commits[it] }
  }

  private val spaceReviewChange: MutableProperty<ListSelection<SpaceReviewChange>> =
    mutableProperty(ListSelection.create(emptyList<SpaceReviewChange>(), null))

  val spaceDiffVm: Property<SpaceDiffVm> = mutableProperty(
    SpaceDiffVmImpl(client, reviewId, reviewKey as String, projectKey, selectedCommits, spaceReviewChange,
                    SpaceReviewDiffLoader(lifetime, client)))

  val changesVm: SpaceReviewChangesVm = SpaceReviewChangesVmImpl(
    lifetime, client, projectKey, review.value.identifier,
    reviewId, selectedCommits, participantsVm, spaceReviewChange, infoByRepos
  )
}

internal class MergeRequestDetailsVm(
  lifetime: Lifetime,
  ideaProject: Project,
  spaceProjectInfo: SpaceProjectInfo,
  spaceReposInfo: Set<SpaceRepoInfo>,
  refMrRecord: Ref<MergeRequestRecord>,
  client: KCircletClient
) : SpaceReviewDetailsVm<MergeRequestRecord>(lifetime, ideaProject, spaceProjectInfo, spaceReposInfo, refMrRecord, client) {

  private val branchPair: Property<MergeRequestBranchPair> = cellProperty { review.live.branchPair }

  val repository: Property<String> = cellProperty { branchPair.live.repository }
  val targetBranchInfo: Property<MergeRequestBranch?> = cellProperty { branchPair.live.targetBranchInfo }
  val sourceBranchInfo: Property<MergeRequestBranch?> = cellProperty { branchPair.live.sourceBranchInfo }
}

internal class CommitSetReviewDetailsVm(
  lifetime: Lifetime,
  ideaProject: Project,
  spaceProjectInfo: SpaceProjectInfo,
  spaceReposInfo: Set<SpaceRepoInfo>,
  refMrRecord: Ref<CommitSetReviewRecord>,
  client: KCircletClient
) : SpaceReviewDetailsVm<CommitSetReviewRecord>(lifetime, ideaProject, spaceProjectInfo, spaceReposInfo, refMrRecord, client)

internal fun createReviewDetailsVm(lifetime: Lifetime,
                                   project: Project,
                                   client: KCircletClient,
                                   spaceProjectInfo: SpaceProjectInfo,
                                   spaceReposInfo: Set<SpaceRepoInfo>,
                                   codeReviewListItem: CodeReviewListItem): SpaceReviewDetailsVm<out CodeReviewRecord> {
  return when (val codeReviewRecord = codeReviewListItem.review.resolve()) {
    is MergeRequestRecord -> MergeRequestDetailsVm(
      lifetime,
      project,
      spaceProjectInfo,
      spaceReposInfo,
      codeReviewRecord.toRef(client.arena),
      client
    )
    is CommitSetReviewRecord -> CommitSetReviewDetailsVm(
      lifetime,
      project,
      spaceProjectInfo,
      spaceReposInfo,
      codeReviewRecord.toRef(client.arena),
      client
    )
    else -> throw IllegalArgumentException("Unable to resolve CodeReviewRecord")
  }
}

data class ReviewCommitListItem(
  val commitWithGraph: GitCommitWithGraph,
  val repositoryInReview: RepositoryInReview,
  val index: Int,
  val commitsInRepository: Int,
  val spaceRepoInfo: SpaceRepoInfo?
) {
  val inCurrentProject: Boolean = spaceRepoInfo != null
}