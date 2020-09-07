package com.intellij.space.vcs.review.details

import circlet.client.api.Navigator
import circlet.client.api.ProjectKey
import circlet.client.api.TD_MemberProfile
import circlet.client.api.identifier
import circlet.code.api.*
import circlet.code.codeReview
import circlet.platform.api.BatchInfo
import circlet.platform.api.Ref
import circlet.platform.api.TID
import circlet.platform.client.KCircletClient
import circlet.platform.client.property
import circlet.platform.client.resolve
import circlet.platform.client.resolveRefsOrFetch
import com.intellij.openapi.project.Project
import com.intellij.space.settings.SpaceSettings
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.*

internal open class CrDetailsVm<R : CodeReviewRecord>(
  final override val lifetime: Lifetime,
  val ideaProject: Project,
  private val reviewRef: Ref<R>,
  val client: KCircletClient
) : Lifetimed {

  val review: Property<R> = reviewRef.property()

  val projectKey: ProjectKey = review.value.project
  val reviewKey: String? = review.value.key

  val reviewUrl: String = buildReviewUrl(projectKey, review.value.number)

  val reviewId: TID = review.value.id

  val title: Property<String> = cellProperty { review.live.title }

  val state: Property<CodeReviewState> = cellProperty { review.live.state }

  val createdAt: Property<Long> = cellProperty { review.live.createdAt }

  val createdBy: Property<TD_MemberProfile> = cellProperty { review.live.createdBy!!.resolve() }

  val turnBased: Property<Boolean?> = cellProperty { review.live.turnBased }

  private val participantsProperty: Property<LoadingValue<Ref<CodeReviewParticipants>>> = load {
    client.arena.resolveRefsOrFetch {
      reviewRef.extensionRef(CodeReviewParticipants::class)
    }
  }

  private val participantsRef: Property<Ref<CodeReviewParticipants>?> = lastLoadedValueOrNull(participantsProperty)

  val participantsVm: Property<SpaceReviewParticipantsVm?> = seqMap(participantsRef) { r ->
    r?.let { SpaceReviewParticipantsVmImpl(it, projectKey, review.value.identifier, client, lifetime) }
  }

  protected val detailedInfo = mapInit(review, null) {
    client.codeReview.getReviewDetails(review.value.project.identifier, review.value.identifier)
  }

  val selectedCommit: MutableProperty<GitCommitWithGraph?> = Property.createMutable(null)
}

internal class MergeRequestDetailsVm(
  lifetime: Lifetime,
  ideaProject: Project,
  refMrRecord: Ref<MergeRequestRecord>,
  client: KCircletClient
) : CrDetailsVm<MergeRequestRecord>(lifetime, ideaProject, refMrRecord, client) {

  private val branchPair: Property<MergeRequestBranchPair> = cellProperty { review.live.branchPair }

  val repository: Property<String> = cellProperty { branchPair.live.repository }
  val targetBranchInfo = cellProperty { branchPair.live.targetBranchInfo }
  val sourceBranchInfo = cellProperty { branchPair.live.sourceBranchInfo }

  val commits = mapInit(detailedInfo, null) { detailedInfo ->
    detailedInfo?.commits
  }

  val changes = mapInit(commits, selectedCommit, null) { allCommits, selectedCommit ->
    val revisions = if (selectedCommit != null) {
      listOf(RevisionInReview(selectedCommit.repositoryName, selectedCommit.commit.id))
    }
    else allCommits?.map { it.commits }?.flatten()?.toList()?.map { RevisionInReview(it.repositoryName, it.commit.id) }

    revisions?.let { client.codeReview.getReviewChanges(BatchInfo("0", 1024), projectKey.identifier, reviewId, it).data }
  }
}

internal class CommitSetReviewDetailsVm(
  lifetime: Lifetime,
  ideaProject: Project,
  refMrRecord: Ref<CommitSetReviewRecord>,
  client: KCircletClient
) : CrDetailsVm<CommitSetReviewRecord>(lifetime, ideaProject, refMrRecord, client) {
  val commitsByRepos = mapInit(detailedInfo, null) { _detailedInfo ->
    _detailedInfo?.commits?.associateBy(
      RevisionsInReview::repository,
      RevisionsInReview::commits
    )
  }

  val changesByRepos = mapInit(commitsByRepos, selectedCommit, null) { commitsByRepos, selectedCommit ->
    commitsByRepos ?: return@mapInit null

    val selectedCommitsByRepo = if (selectedCommit != null) {
      commitsByRepos.entries.filter { it.key.name == selectedCommit.repositoryName }
        .map { entry ->
          entry.key to entry.value.filter { commitWithGraph -> commitWithGraph.commit == selectedCommit.commit }
        }.toMap()
    }
    else commitsByRepos
    return@mapInit selectedCommitsByRepo.entries
      .associateBy(
        { it.key },
        {
          val revisions = it.value.map { RevisionInReview(it.repositoryName, it.commit.id) }
          client.codeReview.getReviewChanges(BatchInfo("0", 1024), projectKey.identifier, reviewId, revisions).data
        }
      )
  }
}

fun buildReviewUrl(projectKey: ProjectKey, reviewNumber: Int): String {
  return Navigator.p.project(projectKey)
    .review(reviewNumber)
    .absoluteHref(SpaceSettings.getInstance().serverSettings.server)
}

