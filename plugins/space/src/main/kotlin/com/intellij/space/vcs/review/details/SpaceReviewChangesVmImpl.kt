// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details

import circlet.client.api.ProjectKey
import circlet.client.api.identifier
import circlet.code.api.*
import circlet.code.codeReview
import circlet.platform.api.InitializedChannel
import circlet.platform.api.TID
import circlet.platform.client.FluxSourceItem
import circlet.platform.client.KCircletClient
import circlet.platform.client.durableInitializedFluxChannel
import com.intellij.openapi.ListSelection
import com.intellij.space.SpaceVmWithClient
import com.intellij.space.vcs.SpaceRepoInfo
import kotlinx.coroutines.channels.ReceiveChannel
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.launch
import libraries.coroutines.extra.nested
import runtime.Ui
import runtime.batch.Batch
import runtime.batch.BatchInfo
import runtime.reactive.*
import runtime.reactive.property.mapInit

private const val MAX_CHANGES_TO_LOAD = 1024

internal class SpaceReviewChangesVmImpl(
  override val lifetime: Lifetime,
  override val client: KCircletClient,
  override val projectKey: ProjectKey,
  override val reviewIdentifier: ReviewIdentifier,
  override val reviewId: TID,
  override val allCommits: Property<List<SpaceReviewCommitListItem>>,
  override val participantsVm: Property<SpaceReviewParticipantsVm?>,
  override val infoByRepos: Map<String, SpaceRepoInfo>,
) : SpaceReviewChangesVm,
    SpaceVmWithClient {

  private val lifetimeSource = lifetime.nested()

  override val selectedChanges: MutableProperty<ListSelection<SpaceReviewChange>> =
    mutableProperty(ListSelection.create(emptyList<SpaceReviewChange>(), null))

  override val selectedCommitIndices: MutableProperty<List<Int>> = mutableProperty(emptyList())

  override val selectedCommits: Property<List<SpaceReviewCommitListItem>> =
    mapInit(selectedCommitIndices, allCommits, emptyList()) { indices, commits ->
      if (indices.isEmpty() || commits.isEmpty()) return@mapInit commits

      indices.map { commits[it] }
    }

  override val changes: LoadingProperty<Map<String, ChangesWithDiscussion>> = load(selectedCommits) { selectedCommits ->
    selectedCommits
      .map { RevisionInReview(it.repositoryInReview.name, it.commitWithGraph.commit.id) }
      .groupBy { it.repository }
      .map { it.key to loadChanges(lifetimeSource, it.value, it.key) }
      .toMap()
  }

  private suspend fun loadChanges(lt: LifetimeSource,
                                  revisions: List<RevisionInReview>,
                                  repository: String): ChangesWithDiscussion {
    val reviewChanges: InitializedChannel<DiscussionEvent, DiscussionChannelInitialState<Batch<ChangeInReview>>> = client.codeReview.getReviewChanges(
      lt,
      BatchInfo("0", MAX_CHANGES_TO_LOAD),
      projectKey.identifier,
      reviewId,
      revisions)

    val observableMutableMap = createObservableMap(lt, reviewChanges)
    return ChangesWithDiscussion(reviewChanges.initial.payload.data, observableMutableMap, infoByRepos[repository])
  }

  private fun createObservableMap(lifetime: Lifetime,
                                  initializedChannel: InitializedChannel<DiscussionEvent, DiscussionChannelInitialState<Batch<ChangeInReview>>>): ObservableMutableMap<TID, PropagatedCodeDiscussion> {
    val initializedFluxChannel = durableInitializedFluxChannel(
      lifetime,
      client,
      { throwable, tryNumber ->
        println(throwable)
        tryNumber > 0
      }
    ) { initializedChannel }

    return initializedFluxChannel.toDiscussionObservableMap(lifetime, initializedChannel.initial.discussions) {
      it.discussions
    }
  }
}

private fun <TInit> ReceiveChannel<FluxSourceItem<DiscussionEvent, TInit>>.toDiscussionObservableMap(
  lifetime: Lifetime,
  initial: List<PropagatedCodeDiscussion>? = null,
  getDiscussions: (TInit) -> List<PropagatedCodeDiscussion>
): ObservableMutableMap<TID, PropagatedCodeDiscussion> {
  val discussions = ObservableMutableMap.create(initial?.associateBy { it.discussion.id } ?: emptyMap())
  val channel = this@toDiscussionObservableMap

  lifetime.launch(Ui) {
    for (item in channel) {
      try {
        when (item) {
          is FluxSourceItem.Init -> {
            discussions.init(getDiscussions(item.value))
          }
          is FluxSourceItem.Update -> {
            discussions.update(item)
          }
        }
      }
      catch (e: Exception) {
        println(e)
      }
    }
  }

  return discussions
}

fun ObservableMutableMap<TID, PropagatedCodeDiscussion>.init(discussions: List<PropagatedCodeDiscussion>) {
  clear()
  putAll(discussions.associateBy { it.discussion.id })
}

fun <TInit> ObservableMutableMap<TID, PropagatedCodeDiscussion>.update(item: FluxSourceItem.Update<DiscussionEvent, TInit>) {
  when (val event: DiscussionEvent = item.value) {
    is DiscussionEvent.Created -> put(event.discussion.discussion.id, event.discussion)
    is DiscussionEvent.Removed -> {
    }
  }
}