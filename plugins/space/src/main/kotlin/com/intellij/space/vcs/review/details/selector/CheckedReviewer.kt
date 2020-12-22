// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import circlet.client.api.TD_MemberProfile
import circlet.platform.api.TID
import runtime.reactive.Property

internal data class CheckedReviewer(
    val reviewer: TD_MemberProfile,
    val checked: Property<Set<TID>>
)
