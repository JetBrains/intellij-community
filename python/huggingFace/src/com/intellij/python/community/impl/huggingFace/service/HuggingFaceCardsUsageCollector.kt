// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind

object HuggingFaceCardsUsageCollector: CounterUsagesCollector() {  // PY-70535
  private val GROUP = EventLogGroup("python.hugging.face.cards", 1)

  private val eventEntityKind = EventFields.Enum<HuggingFaceEntityKind>("entity_kind")

  val CARD_SHOWN_ON_HOVER = GROUP.registerEvent(
    "card.shown.on.hover",
    EventFields.StringValidatedByInlineRegexp("entity-name", """[a-zA-Z0-9\.]+/[a-zA-Z0-9\.]+"""),
    eventEntityKind
  )

  val NAVIGATION_LINK_IN_EDITOR_CLICKED = GROUP.registerEvent(
    "navigation.link.clicked",
    EventFields.StringValidatedByInlineRegexp("entity-name", """[a-zA-Z0-9\.]+/[a-zA-Z0-9\.]+"""),
    eventEntityKind
  )

  // todo:
  //card shown in Documentation toolwindow + card type (model, dataset)
  // link clicked in the card + card type (model, dataset) + link type (signature 'powered by Hugging Face' or
  // card name or any link in the description)

  override fun getGroup(): EventLogGroup = GROUP
}
