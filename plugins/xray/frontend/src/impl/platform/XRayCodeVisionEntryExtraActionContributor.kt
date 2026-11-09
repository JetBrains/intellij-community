// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xray.frontend.impl.platform

import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.codeInsight.codeVision.ui.CodeVisionEntryExtraActionContributor
import com.intellij.codeInsight.hints.CodeVisionInlayHintSettingsKey
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.xray.frontend.api.InlayHintStage
import com.intellij.xray.frontend.api.XRayBundle
import com.intellij.xray.frontend.impl.action.applyStageChange
import com.intellij.xray.frontend.impl.action.showSettingsFor
import com.intellij.xray.frontend.impl.action.stageVisibilitySummary
import com.intellij.xray.frontend.impl.action.xrayStageChoices
import com.intellij.xray.frontend.impl.service.XRayStageStore
import org.jetbrains.annotations.Nls

private const val OPEN_SETTINGS_ACTION_ID = "!XRayOpenSettings"

private const val XRAY_STAGE_PREFIX = "!XRayStage:"

internal class XRayCodeVisionEntryExtraActionContributor : CodeVisionEntryExtraActionContributor {

  override fun contribute(
    project: Project,
    groupId: String,
  ): List<CodeVisionEntryExtraActionModel> {
    val store = XRayStageStore.getInstanceOrNull() ?: return emptyList()

    val current =
      store.getStage(CodeVisionInlayHintSettingsKey(groupId = groupId))
    val children = xrayStageChoices(current).map {
      CodeVisionEntryExtraActionModel(it.title, "$XRAY_STAGE_PREFIX${it.stage.name}")
    }.toMutableList()
    children.add(CodeVisionEntryExtraActionModel(XRayBundle.message("action.open.in.settings.text"),
                                                 OPEN_SETTINGS_ACTION_ID))
    return listOf(CodeVisionEntryExtraActionModel(XRayBundle.message("xray.inlay.menu.change"), children))
  }

  override fun handle(
    editor: Editor,
    groupId: String,
    actionId: String,
  ): Boolean {

    val project = editor.project ?: return false
    val store = XRayStageStore.getInstanceOrNull() ?: return false
    val key = CodeVisionInlayHintSettingsKey(groupId = groupId)

    if (actionId.startsWith(XRAY_STAGE_PREFIX)) {
      val stage = InlayHintStage.valueOf(actionId.removePrefix(XRAY_STAGE_PREFIX))
      val previousStage = store.getStage(key)

      return applyStageChange(editor, key, stage, previousStage) {
        showSettingsFor(project, key)
      }
    }

    if (actionId == OPEN_SETTINGS_ACTION_ID) {
      showSettingsFor(project, key)
      return true
    }

    return false
  }

  override fun adText(project: Project, groupId: String): @Nls String {
    val store = XRayStageStore.getInstanceOrNull() ?: return ""
    return stageVisibilitySummary(
      store.getStage(CodeVisionInlayHintSettingsKey(groupId = groupId)))
  }
}
