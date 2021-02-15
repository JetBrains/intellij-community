// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.participants

import com.intellij.space.vcs.review.details.selector.ParticipantStatusBadgeKind
import com.intellij.ui.JBColor
import java.awt.Color

internal val ParticipantStatusBadgeKind.borderColor: Color
  get() = when (this) {
    ParticipantStatusBadgeKind.ACCEPTED -> JBColor.GREEN
    ParticipantStatusBadgeKind.WORKING -> JBColor.ORANGE
    ParticipantStatusBadgeKind.WAITING -> JBColor.GRAY
    ParticipantStatusBadgeKind.REJECTED -> JBColor.RED
  }