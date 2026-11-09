// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.service

import com.intellij.codeInsight.hints.InlayHintsRefresher
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xray.frontend.api.XRayMode
import com.intellij.xray.frontend.api.editorTracking.XRAY_CURRENT_LINE
import com.intellij.xray.frontend.api.editorTracking.XRAY_CURRENT_LINE_POSITION
import com.intellij.xray.frontend.api.editorTracking.XRAY_EDITOR_MODE
import com.intellij.xray.frontend.api.editorTracking.XRAY_EDITOR_USE_MODE
import com.intellij.xray.frontend.api.editorTracking.XRayEditorUseMode

private const val REFRESH_REASON = "X-Ray mode"

/**
 * The main service for X-Ray Mode feature.
 *
 * The service is the parent disposable for the peek gestures. Therefore, we need to make it implement [Disposable].
 */
@Service(Service.Level.APP)
internal class XRayModeService : Disposable {

  companion object {
    @JvmStatic
    fun getInstance(): XRayModeService = application.service()
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

  fun setDebuggingUseMode(project: Project, debugging: Boolean) {
    val mode = if (debugging) XRayEditorUseMode.DEBUGGING else XRayEditorUseMode.READING
    for (textEditor in projectTextEditors(project)) {
      textEditor.editor.putUserData(XRAY_EDITOR_USE_MODE, mode)
    }
    refreshProjectHints(project)
  }

  @RequiresEdt
  fun refreshActiveEditorHints(editor: Editor) {
    editor.putUserData(XRAY_EDITOR_MODE, xRayMode)
    InlayHintsRefresher.refreshEditor(editor, REFRESH_REASON)
  }

  @RequiresEdt
  fun refreshAllHints() {
    for (project in ProjectManager.getInstance().openProjects) {
      if (project.isDisposed) continue
      refreshProjectHints(project)
    }
  }

  @RequiresEdt
  fun refreshProjectHints(project: Project) {
    for (textEditor in projectTextEditors(project)) {
      textEditor.editor.putUserData(XRAY_EDITOR_MODE, xRayMode)
    }
    InlayHintsRefresher.refreshAll(project, REFRESH_REASON)
  }

  private fun projectTextEditors(project: Project): List<TextEditor> =
    FileEditorManager.getInstance(project).allEditors.filterIsInstance<TextEditor>()

  override fun dispose() {}

}
