// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.MarkdownBundle

internal object TrustedProjectUtil {
  /**
   * Executes [block] only if [project] is trusted.
   * If it's trusted state is unknown, will show trusted project confirmation dialog and
   * execute [block] depending on the dialog result.
   */
  fun executeIfTrusted(project: Project, block: () -> Unit): Boolean {
    if (TrustedProjects.isProjectTrusted(project) || confirmProjectIsTrusted(project)) {
      block.invoke()
      return true
    }
    return false
  }

  private fun confirmProjectIsTrusted(project: Project): Boolean {
    return TrustedProjectsDialog.confirmLoadingUntrustedProject(
      project = project,
      message = MarkdownBundle.message("markdown.untrusted.project.dialog.text"),
    )
  }
}
