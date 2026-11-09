// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.action

import com.intellij.codeInsight.hints.InlayHintSettingsKey
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.NlsActions
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.xray.frontend.api.InlayHintStage
import com.intellij.xray.frontend.api.XRayBundle
import com.intellij.xray.frontend.impl.service.XRayModeService
import com.intellij.xray.frontend.impl.service.XRayStageStore
import org.jetbrains.annotations.Nls

internal data class XRayStageChoice(val stage: InlayHintStage, @NlsActions.ActionText val title: String)

internal fun xrayStageChoices(currentStage: InlayHintStage): List<XRayStageChoice> {
  return InlayHintStage.entries.filter { it != currentStage }.map { stage ->
    val promotion = stage.visibilityRank > currentStage.visibilityRank
    val key = if (promotion) "xray.inlay.menu.promote.to" else "xray.inlay.menu.demote.to"
    XRayStageChoice(stage, XRayBundle.message(key, stageName(stage)))
  }
}

internal fun stageName(stage: InlayHintStage): @Nls String = when (stage) {
  InlayHintStage.ALWAYS -> XRayBundle.message("xray.inlay.stage.always")
  InlayHintStage.ESSENTIAL -> XRayBundle.message("xray.inlay.stage.essential")
  InlayHintStage.ADDITIONAL -> XRayBundle.message("xray.inlay.stage.additional")
  InlayHintStage.DISABLED -> XRayBundle.message("xray.inlay.stage.disabled")
}

internal fun stageVisibilitySummary(stage: InlayHintStage): @Nls String = when (stage) {
  InlayHintStage.ALWAYS -> XRayBundle.message("xray.inlay.stage.summary.always")
  InlayHintStage.ESSENTIAL -> XRayBundle.message("xray.inlay.stage.summary.essential")
  InlayHintStage.ADDITIONAL -> XRayBundle.message("xray.inlay.stage.summary.additional")
  InlayHintStage.DISABLED -> XRayBundle.message("xray.inlay.stage.summary.disabled")
}

internal fun applyStageChange(
  editor: Editor,
  key: InlayHintSettingsKey,
  stage: InlayHintStage,
  previousStage: InlayHintStage,
  inlaySettingsDisplay: () -> Boolean,
): Boolean {

  val project = editor.project ?: return false
  val service = XRayModeService.getInstance()
  val store = XRayStageStore.getInstanceOrNull() ?: return false

  if (IdeProductMode.isFrontend) return true

  CommandProcessor.getInstance().executeCommand(project, {
    store.setStage(key, stage)
    service.refreshAllHints()

    UndoManager.getInstance(project).undoableActionPerformed(
      UndoInlayVisibilityChangeAction(editor.document, project, key, stage, previousStage)
    )

    if (stage == InlayHintStage.DISABLED) {
      NotificationGroupManager.getInstance().getNotificationGroup("X-Ray Inlay Visibility")
        .createNotification(XRayBundle.message("xray.inlay.undo.balloon", stageName(stage)),
                            NotificationType.INFORMATION)
        .addAction(NotificationAction.createSimpleExpiring(XRayBundle.message("xray.inlay.undo.action")) {
          val fileEditor = FileEditorManager.getInstance(project).selectedEditor
          val undo = UndoManager.getInstance(project)
          if (fileEditor != null && undo.isUndoAvailable(fileEditor)) undo.undo(fileEditor)
        })
        .addAction(NotificationAction.createSimpleExpiring(XRayBundle.message("xray.inlay.undo.open_settings")) {
          inlaySettingsDisplay()
        })
        .notify(project)
    }
  }, "", null, editor.document)

  return true
}
