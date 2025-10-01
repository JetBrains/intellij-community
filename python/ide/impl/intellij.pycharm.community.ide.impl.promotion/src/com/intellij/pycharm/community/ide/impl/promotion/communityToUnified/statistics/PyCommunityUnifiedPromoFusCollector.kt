// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object PyCommunityUnifiedPromoFusCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private const val GROUP_ID = "pycharm.community.to.unified.promo"
  private val GROUP = EventLogGroup(GROUP_ID, 1)

  // Enums
  enum class BannerControl { UPDATE_NOW, LEARN_MORE }
  enum class TooltipCloseReason { DISMISSED, UPDATE_NOW }
  enum class PromoModalCloseReason { DISMISSED, UPDATE_NOW }
  enum class UpdateDialogCloseReason { IGNORE, REMIND_LATER, UPDATE_NOW }

  // Fields
  private val BannerControlField = EventFields.Enum("control", BannerControl::class.java) { it.name.lowercase() }
  private val TooltipCloseReasonField = EventFields.Enum("reason", TooltipCloseReason::class.java) { it.name.lowercase() }
  private val PromoModalCloseReasonField = EventFields.Enum("reason", PromoModalCloseReason::class.java) { it.name.lowercase() }
  private val UpdateDialogCloseReasonField = EventFields.Enum("reason", UpdateDialogCloseReason::class.java) { it.name.lowercase() }
  private val DurationMsField = EventFields.Long("duration_ms")

  // Welcome screen banner
  internal val WelcomeScreenBannerShown = GROUP.registerEvent("welcome.banner.shown")
  internal val WelcomeScreenBannerClicked = GROUP.registerEvent("welcome.banner.clicked", BannerControlField)

  // Tooltip
  internal val TooltipShown = GROUP.registerEvent("tooltip.shown")
  internal val TooltipClosed = GROUP.registerEvent("tooltip.closed", TooltipCloseReasonField)

  // Promo modal dialog
  internal val PromoModalShown = GROUP.registerEvent("promo.modal.shown")
  internal val PromoModalClosed = GROUP.registerEvent("promo.modal.closed", PromoModalCloseReasonField, DurationMsField)

  // Update dialog
  internal val UpdateDialogShown = GROUP.registerEvent("update.dialog.shown")
  internal val UpdateDialogClosed = GROUP.registerEvent("update.dialog.closed", UpdateDialogCloseReasonField, DurationMsField)

  // Update ready restart notification (balloon)
  internal val UpdateReadyRestartNotificationShown = GROUP.registerEvent("update.ready.restart.notification.shown")
}