// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.rules.impl.LocalFileCustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object HuggingFaceCardsUsageCollector: CounterUsagesCollector() {  // PY-70535
  private val GROUP = EventLogGroup("python.hugging.face.cards", 2)

  // todo:
  // card shown in Documentation toolwindow + card type (model, dataset)
  // link clicked in the card + card type (model, dataset) + link type (signature 'powered by Hugging Face' or card name or any link in the description)
  // consider moving "HuggingFacePipelineTags.txt" to resources directory
  // may be helpful in future:
  // private val eventEntityKind = EventFields.Enum<HuggingFaceEntityKind>("entity_kind")
  // private val entityNameField = EventFields.StringValidatedByInlineRegexp("entity-name", """[a-zA-Z0-9\.]+/[a-zA-Z0-9\.]+""")

  private val pipelineTag = EventFields.StringValidatedByCustomRule("pipeline_tag", HuggingFacePipelineTagWhitelistRule::class.java)

  val CARD_SHOWN_ON_HOVER = GROUP.registerEvent(
    "card.shown.on.hover",
    pipelineTag
  )

  val NAVIGATION_LINK_IN_EDITOR_CLICKED = GROUP.registerEvent(
    "navigation.link.clicked",
    pipelineTag
  )

  override fun getGroup(): EventLogGroup = GROUP

  class HuggingFacePipelineTagWhitelistRule private constructor() :
    LocalFileCustomValidationRule("hugging_face_pipeline_tag",
                                  HuggingFaceCardsUsageCollector::class.java,
                                  "HuggingFacePipelineTags.txt"
    )
}
