// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import circlet.client.api.ProjectIdentifier
import circlet.client.api.TD_MemberProfile
import circlet.client.api.match
import circlet.client.pr
import circlet.code.api.*
import circlet.code.codeReview
import circlet.platform.api.Ref
import circlet.platform.client.*
import com.intellij.space.vcs.review.details.CommitSetReviewDetailsVm
import com.intellij.space.vcs.review.details.MergeRequestDetailsVm
import com.intellij.space.vcs.review.details.SpaceReviewDetailsVm
import com.intellij.space.vcs.review.details.SpaceReviewParticipantsVm
import libraries.coroutines.extra.Lifetime
import runtime.batch.Batch
import runtime.batch.BatchInfo
import runtime.batch.map
import runtime.reactive.*
import runtime.reactive.property.map
import runtime.reactive.property.mapInit

internal abstract class SpaceReviewParticipantSelectorVmImpl(
  final override val currentParticipants: Property<List<CodeReviewParticipant>>,
  final override val lifetime: Lifetime,
  val client: KCircletClient,
  val getSuggestedParticipants: suspend () -> List<Ref<TD_MemberProfile>>,
  val getPossibleParticipantsBatch: suspend (batch: BatchInfo, searchText: String) -> Batch<Ref<TD_MemberProfile>>,
  val filterParticipants: (TD_MemberProfile) -> Boolean
) : SpaceReviewParticipantSelectorVm {
  private val searchText: MutableProperty<String> = mutableProperty("")

  override val dataUpdateSignal: Signal<DataUpdate> = SignalImpl()

  private val allSuggestedParticipants: Property<List<TD_MemberProfile>?> = mapInit(null) {
    getSuggestedParticipants().resolveAll()
  }

  private val currentParticipantsIds = map(currentParticipants) {
    it.map { participant -> participant.user.resolve().id }
  }

  final override val suggestedParticipants: Property<List<TD_MemberProfile>?> =
    mapInit(searchText, allSuggestedParticipants, emptyList()) { text, suggested ->
      suggested ?: return@mapInit null
      val result = suggested
        .filter { it.match(text) }
        .filter { filterParticipants(it) }
      fireSuggestedUpdate(result)
      result
    }

  private fun fireSuggestedUpdate(result: List<TD_MemberProfile>) {
    if (result.isEmpty()) return
    dataUpdateSignal.fire(DataUpdate.RemoveAll)

    val data = if (result.size == 1) {
      result.map { profile ->
        SpaceReviewParticipantItem(profile, { isSelected(profile) }, SpaceReviewParticipantItemPosition.SINGLE_SUGGESTED)
      }
    }
    else {
      result.mapIndexed { index, profile ->
        val position = when (index) {
          0 -> SpaceReviewParticipantItemPosition.FIRST_SUGGESTED
          result.size - 1 -> SpaceReviewParticipantItemPosition.LAST_SUGGESTED
          else -> SpaceReviewParticipantItemPosition.PLAIN
        }
        SpaceReviewParticipantItem(profile, { isSelected(profile) }, position)
      }
    }
    dataUpdateSignal.fire(DataUpdate.PrependParticipants(data))
  }

  private fun isSelected(profile: TD_MemberProfile): Boolean {
    return currentParticipantsIds.value.contains(profile.id)
  }

  final override val possibleParticipants: Property<XPagedListOnFlux<TD_MemberProfile>> =
    map(suggestedParticipants, searchText) { suggested, text ->
      if (suggested == null) xPagedListOnFlux(client, 1, keyFn = { it.id }, loadImmediately = true) {
        Batch("", 0, emptyList())
      }
      else xPagedListOnFlux(client, 30, keyFn = { it.id }, loadImmediately = true) {
        getPossibleParticipantsBatch(it, text).resolveAll()
      }
    }

  override val isLoading: Property<Boolean> = lifetime.flatten(
    lifetime.map(possibleParticipants) { reviewList ->
      allSuggestedParticipants.value ?: return@map Property.create(true)
      lifetime.mapInit(reviewList.isLoading, false) {
        it
      }
    }
  )

  override fun searchParticipant(searchText: String) {
    dataUpdateSignal.fire(DataUpdate.RemoveAll)
    this.searchText.value = searchText
  }

  init {
    possibleParticipants.view(lifetime) { lt, value ->
      value.batches.forEach(lt) { batchResult ->
        when (batchResult) {
          is BatchResult.More -> {
            val data = batchResult.items
              .filter { !(suggestedParticipants.value?.contains(it) ?: false) }
              .filter { filterParticipants(it) }
              .map { profile ->
                SpaceReviewParticipantItem(profile, { isSelected(profile) }, SpaceReviewParticipantItemPosition.PLAIN)
              }
            dataUpdateSignal.fire(DataUpdate.AppendParticipants(data))
          }
          is BatchResult.Reset -> dataUpdateSignal.fire(DataUpdate.RemoveAll)
        }
      }
    }
  }
}

internal class MergeRequestReviewersSelectorVm(
  reviewers: Property<List<CodeReviewParticipant>>,
  authors: Property<List<CodeReviewParticipant>>,
  projectIdentifier: ProjectIdentifier,
  repository: String,
  branchPair: MergeRequestBranchPair,
  lifetime: Lifetime,
  client: KCircletClient,
) : SpaceReviewParticipantSelectorVmImpl(
  reviewers,
  lifetime,
  client,
  getSuggestedParticipants = suspend {
    client.codeReview.getSuggestedReviewersForMergeRequest(
      projectIdentifier,
      repository,
      branchPair.sourceBranchHead,
      branchPair.targetBranchHead
    ).allSuggestions
  },
  getPossibleParticipantsBatch = { batch: BatchInfo, searchText: String ->
    client.codeReview.getPossibleReviewersForMergeRequest(
      projectIdentifier,
      repository,
      branchPair.targetBranchHead,
      batch,
      searchText
    ).map {
      it.profile
    }
  },
  SingleAuthorCantBeReviewerFilter(authors)::filter
)

internal class CommitSetReviewersSelectorVm(
  reviewers: Property<List<CodeReviewParticipant>>,
  authors: Property<List<CodeReviewParticipant>>,
  projectIdentifier: ProjectIdentifier,
  reviewIdentifier: ReviewIdentifier,
  lifetime: Lifetime,
  client: KCircletClient,
) : SpaceReviewParticipantSelectorVmImpl(
  reviewers,
  lifetime,
  client,
  getSuggestedParticipants = suspend {
    client.codeReview.getSuggestedReviewers(
      projectIdentifier,
      reviewIdentifier
    )
  },
  getPossibleParticipantsBatch = { batch: BatchInfo, searchText: String ->
    client.pr.getMembersWhoCanViewProject(
      batch,
      projectIdentifier,
      searchText
    )
  },
  SingleAuthorCantBeReviewerFilter(authors)::filter
)

internal class AuthorsSelectorVm(
  authors: Property<List<CodeReviewParticipant>>,
  projectIdentifier: ProjectIdentifier,
  lifetime: Lifetime,
  client: KCircletClient,
) : SpaceReviewParticipantSelectorVmImpl(
  authors,
  lifetime,
  client,
  getSuggestedParticipants = suspend { emptyList() },
  getPossibleParticipantsBatch = { batch: BatchInfo, searchText: String ->
    client.pr.getMembersWhoCanViewProject(
      batch,
      projectIdentifier,
      searchText
    )
  },
  { true }
)


/**
 * The part of Space logic for filtering possible reviewers list in selector.
 * Single review's author can't be reviewer
 */
class SingleAuthorCantBeReviewerFilter(val authors: Property<List<CodeReviewParticipant>>) {
  fun filter(profile: TD_MemberProfile): Boolean {
    val singleOrNullAuthor = authors.value.map { it.user }.resolveAll().singleOrNull()
    return profile != singleOrNullAuthor
  }
}

internal fun createSelectorVm(participantsVm: SpaceReviewParticipantsVm,
                              detailsVm: SpaceReviewDetailsVm<*>,
                              projectIdentifier: ProjectIdentifier.Key,
                              lifetime: Lifetime,
                              client: KCircletClient,
                              reviewIdentifier: ReviewIdentifier,
                              role: CodeReviewParticipantRole
): SpaceReviewParticipantSelectorVmImpl = when (role) {
  CodeReviewParticipantRole.Author -> {
    AuthorsSelectorVm(participantsVm.authors, projectIdentifier, lifetime, client)
  }
  CodeReviewParticipantRole.Reviewer -> {
    when (detailsVm) {
      is MergeRequestDetailsVm -> {
        val repository = detailsVm.repository.value
        MergeRequestReviewersSelectorVm(participantsVm.reviewers, participantsVm.authors, projectIdentifier, repository,
                                        detailsVm.branchPair.value, lifetime, client)
      }
      is CommitSetReviewDetailsVm -> {
        CommitSetReviewersSelectorVm(participantsVm.reviewers, participantsVm.authors, projectIdentifier, reviewIdentifier,
                                     lifetime, client)
      }
    }
  }
  else -> error("Unable to create selector for ${role}")
}