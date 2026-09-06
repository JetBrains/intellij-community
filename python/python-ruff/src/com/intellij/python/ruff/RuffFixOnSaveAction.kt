// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener.DocumentUpdatingActionOnSave
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.python.community.execService.Args
import com.intellij.python.pytools.backend.isEnabledOn
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.Result

private val LOG = logger<RuffFixOnSaveAction>()

/**
 * Applies Ruff's safe lint fixes (`ruff check --fix-only`) to a Python file when the file is saved.
 *
 * Enabled for each project with the "Run code fixes on save" option ([RuffConfiguration.fixOnSave]). `--fix-only`
 * applies the project's configured fixable rules and writes the result to stdout, exiting 0 even when unfixable
 * violations remain. Unlike [RuffImportOptimizer], no rule is force-selected: Ruff fixes only what the project enables.
 */
internal class RuffFixOnSaveAction : DocumentUpdatingActionOnSave() {
  override val presentableName: String = "Ruff"

  override fun isEnabledForProject(project: Project): Boolean =
    TrustedProjects.isProjectTrusted(project) &&
    RuffPyTool.getInstance().isEnabledOn(project) &&
    project.service<RuffConfiguration>().fixOnSave

  override suspend fun updateDocument(project: Project, document: Document) {
    val request = readAction {
      val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return@readAction null
      if (virtualFile.fileType != PythonFileType.INSTANCE) return@readAction null
      if (!ProjectFileIndex.getInstance(project).isInContent(virtualFile)) return@readAction null

      // Ruff runs on the interpreter's machine, so the path it is given must be the path there.
      val path = virtualFile.ruffPath() ?: return@readAction null
      Triple(path, ruffScopeOf(project, virtualFile), document.text)
    } ?: return

    val (path, moduleOrProject, originalText) = request

    val fixedText = when (val result = RuffPyTool.getInstance().runOnStdin(
      moduleOrProject,
      Args("check", "--fix-only", "--stdin-filename", path, "-"),
      originalText,
    )) {
      is Result.Success -> result.result
      is Result.Failure -> {
        LOG.warn("Ruff fix-on-save failed for $path: ${result.error.message}")
        return
      }
    }

    if (fixedText != originalText) {
      writeCommandAction(project, presentableName) {
        if (document.text == originalText) {
          document.setText(fixedText)
        }
      }
    }
  }
}
