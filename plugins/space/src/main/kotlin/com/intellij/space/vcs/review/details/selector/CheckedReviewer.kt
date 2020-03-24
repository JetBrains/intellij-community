package com.intellij.space.vcs.review.details.selector

import circlet.client.api.TD_MemberProfile
import circlet.platform.api.TID
import runtime.reactive.Property

internal data class CheckedReviewer(
    val reviewer: TD_MemberProfile,
    val checked: Property<Set<TID>>
)
