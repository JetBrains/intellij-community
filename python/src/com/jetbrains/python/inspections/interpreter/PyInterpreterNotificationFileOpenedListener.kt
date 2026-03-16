// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.interpreter

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications

/**
 * Workaround for split-editor files (e.g. README.md in Markdown plugin).
 *
 * When a file opens in a split editor, some editor components may not be visible yet
 * when the initial [EditorNotifications] update runs. The framework marks them with
 * a pending-update flag, but that flag is only consumed on tab selection change — which
 * does not fire for the initially opened file.
 *
 * This listener re-triggers [EditorNotifications.updateNotifications] from [fileOpened].
 * The call launches a coroutine on EDT, which runs on the next dispatch — by that time
 * the split editor components are laid out and visible.
 */
internal class PyInterpreterNotificationFileOpenedListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    if (file.name !in RELEVANT_NON_PYTHON_FILES) return
    DumbService.getInstance(source.project).runWhenSmart {
      EditorNotifications.getInstance(source.project).updateNotifications(file)
    }
  }
}
