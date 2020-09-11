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
import circlet.platform.client.*
import com.intellij.openapi.project.Project
import com.intellij.space.settings.SpaceSettings
import com.intellij.space.vcs.SpaceProjectInfo
import com.intellij.space.vcs.SpaceRepoInfo
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.*

private const val MAX_CHANGES_TO_LOAD = 1024

internal open class CrDetailsVm<R : CodeReviewRecord>(
  final override val lifetime: Lifetime,
  val ideaProject: Project,
  val spaceProjectInfo: SpaceProjectInfo,
  val spaceReposInfo: Set<SpaceRepoInfo>,
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

  protected val detailedInfo: Property<CodeReviewDetailedInfo?> = mapInit(review, null) {
    client.codeReview.getReviewDetails(review.value.project.identifier, review.value.identifier)
  }

  val commits: Property<List<ReviewCommitListItem>?> = mapInit(detailedInfo, null) { detailedInfo ->
    val spaceReposByName = spaceReposInfo.associateBy(SpaceRepoInfo::name)

    detailedInfo?.commits?.flatMap { revInReview ->
      val repo = revInReview.repository
      val repoInfo = spaceReposByName[repo.name]
      val commitsInRepository = revInReview.commits.size

      revInReview.commits.mapIndexed { index, gitCommitWithGraph ->
        ReviewCommitListItem(gitCommitWithGraph, repo, index, commitsInRepository, repoInfo)
      }
    }
  }

  val selectedCommitIndices: MutableProperty<List<Int>> = Property.createMutable(emptyList())

  protected suspend fun loadChanges(revisions: List<RevisionInReview>): List<ChangeInReview> =
    client.codeReview.getReviewChanges(BatchInfo("0", MAX_CHANGES_TO_LOAD),
                                       projectKey.identifier,
                                       reviewId,
                                       revisions).data
}

internal class MergeRequestDetailsVm(
  lifetime: Lifetime,
  ideaProject: Project,
  spaceProjectInfo: SpaceProjectInfo,
  spaceReposInfo: Set<SpaceRepoInfo>,
  refMrRecord: Ref<MergeRequestRecord>,
  client: KCircletClient
) : CrDetailsVm<MergeRequestRecord>(lifetime, ideaProject, spaceProjectInfo, spaceReposInfo, refMrRecord, client) {

  private val branchPair: Property<MergeRequestBranchPair> = cellProperty { review.live.branchPair }

  val repository: Property<String> = cellProperty { branchPair.live.repository }
  val targetBranchInfo: Property<MergeRequestBranch?> = cellProperty { branchPair.live.targetBranchInfo }
  val sourceBranchInfo: Property<MergeRequestBranch?> = cellProperty { branchPair.live.sourceBranchInfo }

  val repoInfo = spaceReposInfo.firstOrNull { it.name == repository.value }

  val changes = mapInit(commits, selectedCommitIndices, null) { allCommits, selectedCommitIndices ->
    allCommits ?: return@mapInit null

    val selectedCommits = if (selectedCommitIndices.isNotEmpty()) selectedCommitIndices.map { allCommits[it] } else allCommits
    selectedCommits
      .map { RevisionInReview(it.repositoryInReview.name, it.commitWithGraph.commit.id) }
      .let { loadChanges(it) }
  }
}

internal class CommitSetReviewDetailsVm(
  lifetime: Lifetime,
  ideaProject: Project,
  spaceProjectInfo: SpaceProjectInfo,
  spaceReposInfo: Set<SpaceRepoInfo>,
  refMrRecord: Ref<CommitSetReviewRecord>,
  client: KCircletClient
) : CrDetailsVm<CommitSetReviewRecord>(lifetime, ideaProject, spaceProjectInfo, spaceReposInfo, refMrRecord, client) {

  val reposInCurrentProject: Property<Map<String, SpaceRepoInfo?>?> = mapInit(commits, null) { commits ->
    val spaceReposByName = spaceReposInfo.associateBy(SpaceRepoInfo::name)

    commits?.associateBy(
      { it.repositoryInReview.name },
      { spaceReposByName[it.repositoryInReview.name] }
    )
  }

  val changesByRepos: Property<Map<String, List<ChangeInReview>>?> = mapInit(commits, selectedCommitIndices,
                                                                             null) { commits, selectedCommitIndices ->
    commits ?: return@mapInit null

    val selectedCommits = if (selectedCommitIndices.isNotEmpty()) selectedCommitIndices.map { commits[it] } else commits
    selectedCommits
      .map { RevisionInReview(it.repositoryInReview.name, it.commitWithGraph.commit.id) }
      .groupBy { it.repository }
      .map {
        val repoName = it.key
        val revisionsInRepo = it.value
        repoName to loadChanges(revisionsInRepo)
      }.toMap()
  }
}

fun buildReviewUrl(projectKey: ProjectKey, reviewNumber: Int): String {
  return Navigator.p.project(projectKey)
    .review(reviewNumber)
    .absoluteHref(SpaceSettings.getInstance().serverSettings.server)
}

internal fun createReviewDetailsVm(lifetime: Lifetime,
                                   project: Project,
                                   client: KCircletClient,
                                   spaceProjectInfo: SpaceProjectInfo,
                                   spaceReposInfo: Set<SpaceRepoInfo>,
                                   ref: Ref<CodeReviewRecord>): CrDetailsVm<out CodeReviewRecord> {
  return when (val codeReviewRecord = ref.resolve()) {
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