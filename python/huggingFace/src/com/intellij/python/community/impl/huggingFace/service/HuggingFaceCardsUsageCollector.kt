// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants

object HuggingFaceCardsUsageCollector: CounterUsagesCollector() {  // PY-70535
  private val GROUP = EventLogGroup("python.hugging.face.cards", 3)

  // todo:
  // card shown in Documentation toolwindow + card type (model, dataset)
  // link clicked in the card + card type (model, dataset) + link type (signature 'powered by Hugging Face' or card name or any link in the description)
  // open HF site for gated and non-gated models separately
  private val pipelineTag = EventFields.StringValidatedByCustomRule("pipeline_tag", PipelineTagValidationRule::class.java)

  private val modelChoiceEntryPointField = EventFields.Enum<ModelChoiceEntryPointType>("modelChoiceEntryPoint")
  private val modelChoiceActiveFileTypeField = EventFields.Enum<ActiveFileType>("activeFileType")
  private val modelChoiceDialogClosedResulField = EventFields.Enum<ModelChoiceDialogClosedResultType>("closedResultType")
  private val durationField = EventFields.Long("duration_ms")

  enum class ActiveFileType { PY, IPYNB }
  enum class ModelChoiceEntryPointType { CONTEXT_MENU }  // will be extended later
  enum class ModelChoiceDialogClosedResultType { USE_MODEL, CANCEL, CLOSE }

  val CARD_SHOWN_ON_HOVER = GROUP.registerEvent(
    "card.shown.on.hover",
    pipelineTag
  )

  val NAVIGATION_LINK_IN_EDITOR_CLICKED = GROUP.registerEvent(
    "navigation.link.clicked",
    pipelineTag
  )

  val HF_MODEL_CHOICE_DIALOG_OPENED = GROUP.registerEvent(
    "model.choice.dialog.open",
    modelChoiceEntryPointField,
    modelChoiceActiveFileTypeField
  )

  val HF_MODEL_CHOICE_DIALOG_CLOSED = GROUP.registerEvent(
    "model.choice.dialog.closed",
    modelChoiceDialogClosedResulField,
    pipelineTag,  // in case model is chosen, otherwise "undefined"
    durationField
  )

  override fun getGroup(): EventLogGroup = GROUP

  class PipelineTagValidationRule : CustomValidationRule() {
    private val acceptedValues = listOf(
      "audio-classification","audio-to-audio","automatic-speech-recognition","conversational",
      "depth-estimation","document-question-answering","feature-extraction","fill-mask","graph-ml",
      "image-classification","image-segmentation","image-to-3d","image-to-image","image-to-text",
      "image-to-video","mask-generation","object-detection","question-answering","reinforcement-learning",
      "robotics","sentence-similarity","summarization","table-question-answering","tabular-classification",
      "tabular-regression","text-classification","text-generation","text-to-3d","text-to-audio",
      "text-to-image","text-to-speech","text-to-video","text2text-generation","token-classification",
      "translation","unconditional-image-generation","video-classification","visual-question-answering",
      "voice-activity-detection","zero-shot-classification","zero-shot-image-classification",
      "zero-shot-object-detection","text-to-3d","image-to-3d", HuggingFaceConstants.UNDEFINED_PIPELINE_TAG,
      HuggingFaceConstants.DATASET_FAKE_PIPELINE_TAG, HuggingFaceConstants.NO_PIPELINE_TAG
    )


    override fun doValidate(data: String, context: com.intellij.internal.statistic.eventLog.validator.rules.EventContext): ValidationResultType {
      return if (data in acceptedValues) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
    }

    override fun getRuleId(): String = "PipelineTagRule"
  }
}
