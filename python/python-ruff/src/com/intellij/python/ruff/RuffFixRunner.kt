// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.reportSequentialProgress
import com.jetbrains.python.Result

/**
 * Applies `ruff check --fix-only` to each file in [files], and writes each changed result back through its document.
 *
 * Every write happens in one write command action named [commandName]. So the whole run is a single undoable step, and
 * the changes reach the IDE as normal document edits instead of as an external file rewrite. Ruff reads each file over
 * stdin, which fixes an unsaved editor change too.
 *
 * Ruff runs once for each file, not once for a batch of paths. A batch lets Ruff rewrite the files on disk itself,
 * which is faster, but the user cannot then undo the run.
 *
 * No rule is force-selected: Ruff fixes only what the project configures.
 *
 * A read-only file is skipped, and it is not a failure. A write to it would throw and stop the write for all the files.
 *
 * Returns one message for each file Ruff could not process, and an empty list when every file went through.
 */
internal suspend fun applyRuffFixes(
  project: Project,
  files: List<VirtualFile>,
  commandName: @NlsContexts.Command String,
): List<String> {
  val ruff = RuffPyTool.getInstance()
  val fixes = mutableListOf<RuffFix>()
  val failures = mutableListOf<String>()

  reportSequentialProgress(files.size) { reporter ->
    for (file in files) {
      reporter.itemStep(file.name)
      val path = file.ruffPath() ?: continue
      val snapshot = readAction {
        if (!file.isValid || !file.isWritable) return@readAction null
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return@readAction null
        if (!document.isWritable) return@readAction null
        document to document.text
      } ?: continue
      val (document, originalText) = snapshot

      val args = ruffStdinArgs(path, "check", "--fix-only")
      when (val result = ruff.runOnStdin(ruffScopeOf(project, file), args, originalText)) {
        is Result.Success -> if (result.result != originalText) fixes += RuffFix(document, originalText, result.result)
        is Result.Failure -> failures += "${file.name}: ${result.error.message}"
      }
    }
  }

  if (fixes.isNotEmpty()) {
    writeCommandAction(project, commandName) {
      for (fix in fixes) {
        // Skip a document that changed, or became read-only, while Ruff was running.
        if (fix.document.isWritable && fix.document.text == fix.originalText) fix.document.setText(fix.fixedText)
      }
    }
  }
  return failures
}

private class RuffFix(val document: Document, val originalText: String, val fixedText: String)
