// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.python

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.runReadAction
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.jetbrains.python.psi.resolve.PackageAvailabilitySpec
import com.jetbrains.python.psi.resolve.isPackageAvailable

/**
 * User : catherine
 */
object RestPythonUtil {
  private val SPHINX_MARKER = PackageAvailabilitySpec("Sphinx", "sphinx.application.Sphinx")

  /**
   * Enables the Sphinx quickstart when the Python project of the context file has Sphinx. Without a file, the main Python
   * project counts. [SphinxBaseCommand.execute] starts the quickstart for the same Python project. Before the first
   * snapshot, the action is disabled.
   */
  @JvmStatic
  fun updateSphinxQuickStartRequiredAction(e: AnActionEvent): Presentation {
    val presentation = e.presentation
    val project = e.getData(CommonDataKeys.PROJECT) ?: return presentation
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val snapshot = EvoPyProjectModel.getInstance(project).snapshotOrNull()
    val evoPyProject = if (file == null) snapshot?.main else runReadAction { snapshot?.forFile(file) }
    presentation.isEnabled = evoPyProject != null && isPackageAvailable(evoPyProject.pyProject.residesOnModule, SPHINX_MARKER)
    return presentation
  }
}