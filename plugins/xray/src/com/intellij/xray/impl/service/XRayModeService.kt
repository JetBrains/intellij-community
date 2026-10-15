// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.impl.service

import com.intellij.codeInsight.hints.InlayHintsRefresher
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xray.api.XRayMode
import com.intellij.xray.api.editorTracking.XRAY_CURRENT_LINE
import com.intellij.xray.api.editorTracking.XRAY_CURRENT_LINE_POSITION
import com.intellij.xray.api.editorTracking.XRAY_EDITOR_MODE
import com.intellij.xray.api.editorTracking.XRAY_EDITOR_USE_MODE
import com.intellij.xray.api.editorTracking.XRayEditorUseMode

private const val REFRESH_REASON = "X-Ray mode"

@Service(Service.Level.PROJECT)
internal class XRayModeService(private val project: Project) : Disposable {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): XRayModeService = project.service()
  }

  @Volatile
  private var xRayMode: XRayMode = XRayMode.DISABLED

  private var holdActive: Boolean = false

  fun isXRayModeEnabled(): Boolean = xRayMode != XRayMode.DISABLED

  fun getXRayModeState(): XRayMode = xRayMode

  fun isHoldActive(): Boolean = holdActive

  @RequiresEdt
  fun beginPeek() {
    holdActive = true
  }

  @RequiresEdt
  fun endPeek() {
    holdActive = false
  }

  @RequiresEdt
  fun setXRayMode(state: XRayMode, editor: Editor) {

    xRayMode = state
    if (state != XRayMode.DISABLED) {
      editor.putUserData(XRAY_CURRENT_LINE, editor.caretModel.logicalPosition.line)
      editor.putUserData(XRAY_CURRENT_LINE_POSITION, editor.caretModel.logicalPosition.column)
    }

    refreshAllHints()
  }

  @RequiresEdt
  fun cycleXRayMode(editor: Editor) {

    val next = when (xRayMode) {
      XRayMode.DISABLED -> XRayMode.BASIC
      XRayMode.BASIC -> XRayMode.EXTENDED
      XRayMode.EXTENDED -> XRayMode.DISABLED
    }

    setXRayMode(next, editor)
  }

  @RequiresEdt
  fun refreshEditorIfStale(editor: Editor) {
    if ((editor.getUserData(XRAY_EDITOR_MODE) ?: XRayMode.DISABLED) != xRayMode) {
      refreshActiveEditorHints(editor)
    }
  }

  fun setDebuggingUseMode(debugging: Boolean) {

    val mode = if (debugging) XRayEditorUseMode.DEBUGGING else XRayEditorUseMode.READING
    for (textEditor in projectTextEditors()) {
      textEditor.editor.putUserData(XRAY_EDITOR_USE_MODE, mode)
    }

    refreshAllHints()
  }

  @RequiresEdt
  fun refreshActiveEditorHints(editor: Editor) {
    editor.putUserData(XRAY_EDITOR_MODE, xRayMode)
    InlayHintsRefresher.refreshEditor(editor, REFRESH_REASON)
  }

  @RequiresEdt
  fun refreshAllHints() {
    for (textEditor in projectTextEditors()) {
      textEditor.editor.putUserData(XRAY_EDITOR_MODE, xRayMode)
    }
    InlayHintsRefresher.refreshAll(project, REFRESH_REASON)
  }

  private fun projectTextEditors(): List<TextEditor> =
    FileEditorManager.getInstance(project).allEditors.filterIsInstance<TextEditor>()

  override fun dispose() {}

}
