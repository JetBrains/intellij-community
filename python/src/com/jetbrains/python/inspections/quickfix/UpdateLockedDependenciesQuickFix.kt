// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.requirements.getPythonSdk

/**
 * Quick-fix for `[project].dependencies` problems in `pyproject.toml` reported by managers
 * that own a lockfile (Poetry, uv). Invokes [com.jetbrains.python.packaging.management.PythonPackageManager.updateLockedAction] —
 * `poetry update --sync` for Poetry, `uv lock` for uv — instead of feeding individual
 * requirements through `pip install`. Registered only when [com.jetbrains.python.packaging.management.PythonPackageManager.updateLockedAction]
 * returns non-null; managers without a lockfile (pip, conda, …) get the per-package install
 * fixes instead.
 *
 * Refreshing the lock to a state consistent with the manifest is the right action here: the
 * lockfile (not the manifest line) is the source of truth for these managers. The per-item
 * `Install <pkg>` shortcut would otherwise call `poetry add` / `uv add`, which on top of being
 * slower can also rewrite the manifest with a stricter version constraint than the user typed.
 *
 * The label text reuses [com.jetbrains.python.PyBundle]'s `QFIX.NAME.install.all.requirements` so users see the
 * same "Install all missing packages" they'd see for a `requirements.txt` file — the
 * underlying mechanism (lockfile update vs. install-list) is an implementation detail of how
 * the specific package manager satisfies that intent.
 */
internal class UpdateLockedDependenciesQuickFix(
  private val sdk: Sdk,
  private val packageManager: PythonPackageManager,
) : LocalQuickFix, PriorityAction {
  override fun getFamilyName(): String = PyBundle.message("QFIX.NAME.install.all.requirements")

  override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val updateLockedAction = packageManager.updateLockedAction() ?: return
    
    PyPackageCoroutine.launch(project) {
      // Route through PythonPackageManagerUI so the run gets the standard serialized background-progress
      // wrapper plus error-sink reporting; otherwise a sync failure (e.g. `poetry lock` is
      // out of sync) would surface only via logger.warn and the user would see no feedback.
      val pmUI = PythonPackageManagerUI.forSdk(project, sdk)
      pmUI.executeCommand(PyBundle.message("python.packaging.installing.packages")) {
        updateLockedAction().mapSuccess { }
      }
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo =
    IntentionPreviewInfo.EMPTY

  override fun startInWriteAction(): Boolean = false
}