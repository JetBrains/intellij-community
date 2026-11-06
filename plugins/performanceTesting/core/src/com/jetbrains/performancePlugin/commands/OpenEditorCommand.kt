// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.waitForFullyCompleted
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Command opens a file in the editor and measures the open as the [SPAN_NAME] span: from the open request to the loaded
 * text editor. The span leaves out the file lookup, the window focus and the code analysis.
 * Syntax: %openEditor <path relative to the project, or absolute>
 * Example: %openEditor src/Main.kt
 */
class OpenEditorCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {

  companion object {
    const val PREFIX: String = CMD_PREFIX + "openEditor"
    const val SPAN_NAME: String = "openEditor"

    /** Opens [path] through the [FileEditorManager] of [project] and measures it. The IDE that holds the file calls this. */
    @ApiStatus.Internal
    suspend fun openAndMeasure(project: Project, path: String) {
      val file = OpenFileCommand.findFile(path, project) ?: error(PerformanceTestingBundle.message("command.file.not.found", path))
      val manager = project.serviceAsync<FileEditorManager>() as FileEditorManagerEx
      if (manager.isFileOpen(file)) error(PerformanceTestingBundle.message("command.file.already.open", path))
      withContext(Dispatchers.EDT) {
        ProjectUtil.focusProjectWindow(project, stealFocusIfAppInactive = true)
      }
      measureOpen(path) {
        manager.openFile(file, FileEditorOpenOptions(requestFocus = true))
      }
    }

    /** Runs [open] in the [SPAN_NAME] span, which ends when every text editor of the composite that [open] returns is loaded. */
    @ApiStatus.Internal
    suspend fun measureOpen(path: String, open: suspend () -> FileEditorComposite) {
      PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext()).setAttribute("path", path)
        .useWithScope {
          val composite = open()
          check(composite.allEditors.any { it is TextEditor }) { "No text editor opened for $path" }
          waitForFullyCompleted(composite)
        }
    }
  }

  override suspend fun doExecute(context: PlaybackContext) {
    openAndMeasure(context.project, extractCommandArgument(PREFIX).trim())
  }
}
