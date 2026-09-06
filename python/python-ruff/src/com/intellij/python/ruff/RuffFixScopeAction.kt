// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisAction
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.pytools.backend.isEnabledOn
import com.intellij.util.Processor
import com.jetbrains.python.PythonFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LOG = logger<RuffFixScopeAction>()

/**
 * Applies Ruff's safe lint fixes (`ruff check --fix-only`) across a user-chosen scope (project, module, directory, or
 * custom), the project-wide counterpart of the per-file [RuffImportOptimizer] and [RuffFixOnSaveAction].
 *
 * The IDE's inspection-based `Code Cleanup` cannot host Ruff, which is LSP-backed and exposes no `LocalInspectionTool`.
 * So this is a standalone action: it reuses the standard analysis-scope chooser, then hands the scope's Python files to
 * [applyRuffFixes], which writes each fix through the file's document so the user can undo the whole run.
 */
internal class RuffFixScopeAction : BaseAnalysisAction(
  RuffBundle.message("ruff.fix.scope.action.name"),
  RuffBundle.message("ruff.fix.scope.action.name"),
) {
  /**
   * Hides the action unless Ruff can actually run.
   *
   * [BaseAnalysisAction.update] checks only the scope and dumb mode, so without this the action stays enabled in a
   * project that has no Ruff, and [analyze] then returns with no feedback at all.
   */
  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    val canRun = project != null &&
                 TrustedProjects.isProjectTrusted(project) &&
                 RuffPyTool.getInstance().isEnabledOn(project)
    e.presentation.isVisible = canRun
    e.presentation.isEnabled = canRun && e.presentation.isEnabled
  }

  override fun analyze(project: Project, scope: AnalysisScope) {
    if (!TrustedProjects.isProjectTrusted(project)) return
    if (!RuffPyTool.getInstance().isEnabledOn(project)) return

    project.service<RuffService>().cs.launch {
      withBackgroundProgress(project, RuffBundle.message("ruff.fix.scope.progress.title")) {
        val files = collectPythonFiles(scope)
        if (files.isEmpty()) return@withBackgroundProgress

        for (failure in applyRuffFixes(project, files, RuffBundle.message("command.name.apply.ruff.fixes"))) {
          LOG.warn("Ruff fix on scope failed: $failure")
        }
      }
    }
  }

  /**
   * Collects the scope's Python files.
   *
   * [AnalysisScope.accept] takes a read action itself wherever it needs one, so this must not hold one over the whole
   * walk. A single read action over the full content index restarts on every concurrent write action, and in an
   * actively edited project it can restart forever.
   */
  private suspend fun collectPythonFiles(scope: AnalysisScope): List<VirtualFile> = withContext(Dispatchers.IO) {
    val context = currentCoroutineContext()
    val fileTypes = FileTypeRegistry.getInstance()
    buildList {
      scope.accept(Processor { file ->
        context.ensureActive()
        if (!file.isDirectory && fileTypes.isFileOfType(file, PythonFileType.INSTANCE)) add(file)
        true
      })
    }
  }
}
