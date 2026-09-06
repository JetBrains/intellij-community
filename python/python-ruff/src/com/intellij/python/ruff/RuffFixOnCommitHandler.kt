// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.checkin.CommitCheck
import com.intellij.openapi.vcs.checkin.CommitInfo
import com.intellij.openapi.vcs.checkin.CommitProblem
import com.intellij.openapi.vcs.checkin.TextCommitProblem
import com.intellij.openapi.vcs.checkin.committedVirtualFiles
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.platform.util.progress.withProgressText
import com.intellij.python.pytools.backend.isEnabledOn
import com.jetbrains.python.PythonFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = logger<RuffFixOnCommitHandler>()

internal class RuffFixOnCommitHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    RuffFixOnCommitHandler(panel.project)
}

/**
 * Applies Ruff's safe lint fixes (`ruff check --fix-only`) to the committed Python files before the commit completes.
 *
 * Shown as the "Apply Ruff fixes" checkbox in the commit options ("Before Commit" group), the commit-time counterpart
 * of [RuffFixScopeAction] and [RuffFixOnSaveAction]. Like them it runs the Ruff executable directly, because Ruff is
 * LSP-backed and exposes no inspection, so it cannot join the inspection-driven `Code Cleanup` commit check.
 * [applyRuffFixes] writes each fix through the file's document, and the documents are saved after that, which is the
 * order the platform's own [com.intellij.openapi.vcs.checkin.CodeProcessorCheckinHandler] uses.
 */
internal class RuffFixOnCommitHandler(private val project: Project) : CheckinHandler(), CommitCheck {
  private val settings: RuffConfiguration get() = project.service()

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent = RuffFixCommitOption(project, this, settings)

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.MODIFICATION

  override fun isEnabled(): Boolean =
    TrustedProjects.isProjectTrusted(project) &&
    RuffPyTool.getInstance().isEnabledOn(project) &&
    settings.fixOnCommit

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val pythonFiles = readAction {
      val index = ProjectFileIndex.getInstance(project)
      commitInfo.committedVirtualFiles.filter {
        it.isValid && !it.isDirectory && it.fileType == PythonFileType.INSTANCE && index.isInContent(it)
      }
    }
    if (pythonFiles.isEmpty()) return null

    val failures = withContext(Dispatchers.Default) {
      withProgressText(RuffBundle.message("ruff.fix.on.commit.progress.title")) {
        applyRuffFixes(project, pythonFiles, RuffBundle.message("command.name.apply.ruff.fixes"))
      }
    }

    // Persist the fixes so the commit takes the same bytes the documents now hold.
    writeIntentReadAction { FileDocumentManager.getInstance().saveAllDocuments() }

    val firstFailure = failures.firstOrNull() ?: return null
    for (failure in failures) {
      LOG.warn("Ruff fix on commit failed: $failure")
    }
    return TextCommitProblem(RuffBundle.message("ruff.fix.on.commit.failed", firstFailure))
  }
}

/**
 * The "Apply Ruff fixes" commit option, hidden while Ruff is off for the project.
 *
 * The visibility is decided in [restoreState], which the commit UI calls on every refresh, rather than once when the
 * panel is built. Built once, the checkbox would stay missing for the rest of the session after the user enables Ruff.
 */
private class RuffFixCommitOption(project: Project, handler: CheckinHandler, settings: RuffConfiguration) :
  BooleanCommitOption(project, RuffBundle.message("checkbox.checkin.options.apply.ruff.fixes"), false, settings::fixOnCommit) {
  init {
    withCheckinHandler(handler)
  }

  override fun restoreState() {
    super.restoreState()
    setIsVisible(RuffPyTool.getInstance().isEnabledOn(project))
  }
}
