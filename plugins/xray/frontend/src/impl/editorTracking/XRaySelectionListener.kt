// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.editorTracking

import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.xray.frontend.impl.service.XRayModeService

internal class XRaySelectionListener : FileEditorManagerListener {
  override fun selectionChanged(event: FileEditorManagerEvent) {
    val editor = (event.newEditor as? TextEditor)?.editor ?: return
    val service = XRayModeService.getInstance()
    service.refreshEditorIfStale(editor)
  }
}
