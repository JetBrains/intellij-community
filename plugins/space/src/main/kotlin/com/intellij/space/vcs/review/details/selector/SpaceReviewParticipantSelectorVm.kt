// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import circlet.client.api.TD_MemberProfile
import circlet.code.api.CodeReviewParticipant
import circlet.platform.client.XPagedListOnFlux
import libraries.coroutines.extra.Lifetimed
import runtime.reactive.Property
import runtime.reactive.Source

internal interface SpaceReviewParticipantSelectorVm : SpaceLoadingVm, Lifetimed {
  fun searchParticipant(searchText: String)

  val currentParticipants: Property<List<CodeReviewParticipant>>

  val suggestedParticipants: Property<List<TD_MemberProfile>?>

  val possibleParticipants: Property<XPagedListOnFlux<TD_MemberProfile>>

  val dataUpdateSignal: Source<DataUpdate>
}

internal interface SpaceLoadingVm {
  val isLoading: Property<Boolean>
}

internal sealed class DataUpdate {
  object RemoveAll : DataUpdate()

  class AppendParticipants(val data: List<SpaceReviewParticipantItem>) : DataUpdate()

  class PrependParticipants(val data: List<SpaceReviewParticipantItem>) : DataUpdate()
}

internal data class SpaceReviewParticipantItem(
  val profile: TD_MemberProfile,
  val isSelected: () -> Boolean,
  val position: SpaceReviewParticipantItemPosition
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SpaceReviewParticipantItem

    if (profile != other.profile) return false
    if (position != other.position) return false

    return true
  }

  override fun hashCode(): Int {
    var result = profile.hashCode()
    result = 31 * result + position.hashCode()
    return result
  }
}

internal enum class SpaceReviewParticipantItemPosition {
  FIRST_SUGGESTED,
  LAST_SUGGESTED,
  SINGLE_SUGGESTED,
  PLAIN
}
