// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotificationPanel
import java.awt.BorderLayout
import java.util.function.Function
import javax.swing.JComponent

internal class PixiPackageManagerEditorBanner : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (file.name != "pixi.toml") return null
    return Function { fileEditor -> buildPanel(fileEditor) }
  }

  private fun buildPanel(fileEditor: FileEditor): JComponent? {
    val actionManager = ActionManager.getInstance()
    val group = actionManager.getAction("PythonPackageManagerActions") as? ActionGroup ?: return null
    val toolbar = actionManager.createActionToolbar("PixiPackageManagerEditorBanner", group, true).apply {
      targetComponent = fileEditor.component
      setReservePlaceAutoPopupIcon(false)
      component.isOpaque = false
    }
    return EditorNotificationPanel(fileEditor).apply {
      add(toolbar.component, BorderLayout.WEST)
    }
  }
}
