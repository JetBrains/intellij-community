package com.intellij.xray.impl.action

import com.intellij.codeInsight.hints.InlayHintSettingsKey
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.xray.api.InlayHintStage
import com.intellij.xray.impl.service.XRayModeService
import com.intellij.xray.impl.service.XRayStageStore

internal class UndoInlayVisibilityChangeAction(
  document: Document,
  val project: Project,
  val key: InlayHintSettingsKey,
  val stage: InlayHintStage,
  val previousStage: InlayHintStage,
) :
  BasicUndoableAction(document) {
  override fun undo() {
    val service = XRayModeService.getInstance()
    val store = XRayStageStore.getInstanceOrNull() ?: return

    store.setStage(key, previousStage)
    service.refreshAllHints()
  }

  override fun redo() {
    val service = XRayModeService.getInstance()
    val store = XRayStageStore.getInstanceOrNull() ?: return

    store.setStage(key, stage)
    service.refreshAllHints()
  }
}
