// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.selector

import circlet.client.api.TD_MemberProfile

internal interface SpaceReviewParticipantController {
  suspend fun addParticipant(participant: TD_MemberProfile)

  suspend fun removeParticipant(participant: TD_MemberProfile)
}