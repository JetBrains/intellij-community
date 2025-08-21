// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.module

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import java.util.function.Function
import javax.swing.JComponent

internal class PySourceRootDetectedNotificationPanel : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val isEnabled = Registry.`is`("python.source.root.suggest.notification") && Registry.`is`("python.source.root.detection.enabled")
    if (!isEnabled) {
      return null
    }
    if (file.fileType != PythonFileType.INSTANCE) {
      return null
    }
    return Function { editor ->
      when  {
        editor !is TextEditorImpl -> return@Function null
      }
      val pySourceRootDetectionService = project.getService(PySourceRootDetectionService::class.java)
      val sourceRoot = pySourceRootDetectionService.getSourceRootsToReport().firstOrNull() ?: return@Function null

      EditorNotificationPanel(EditorNotificationPanel.Status.Info).apply {
        text = PyBundle.message("python.source.root.detection.editor.notification.title",
                                pySourceRootDetectionService.getSourceRootVisibleName(sourceRoot))

        createActionLabel(PyBundle.message("python.source.root.detection.editor.notification.action.do.label")) {
          pySourceRootDetectionService.markAsSourceRoot(sourceRoot)
        }

        createActionLabel(PyBundle.message("python.source.root.detection.editor.notification.action.dismiss.label")) {
          pySourceRootDetectionService.hideSourceRoot(sourceRoot)
          updateNotification(project)
        }.apply {
          toolTipText = PyBundle.message("python.source.root.detection.editor.notification.action.dismiss.tooltip")
        }
      }
    }
  }

  private fun updateNotification(project: Project) {
    EditorNotifications.getInstance(project).removeNotificationsForProvider(this)
  }
}
