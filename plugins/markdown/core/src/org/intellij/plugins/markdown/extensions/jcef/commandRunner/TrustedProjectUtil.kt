// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions.jcef.commandRunner

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.confirmLoadingUntrustedProject
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.MarkdownBundle

internal object TrustedProjectUtil {
  /**
   * Executes [block] only if [project] is trusted.
   * If it's trusted state is unknown, will show trusted project confirmation dialog and
   * execute [block] depending on the dialog result.
   */
  fun executeIfTrusted(project: Project, block: () -> Unit): Boolean {
    if (project.isTrusted() || confirmProjectIsTrusted(project)) {
      block.invoke()
      return true
    }
    return false
  }

  private fun confirmProjectIsTrusted(project: Project): Boolean {
    return confirmLoadingUntrustedProject(
      project,
      title = IdeBundle.message("untrusted.project.general.dialog.title"),
      message = MarkdownBundle.message("markdown.untrusted.project.dialog.text"),
      trustButtonText = IdeBundle.message("untrusted.project.dialog.trust.button"),
      distrustButtonText = IdeBundle.message("untrusted.project.dialog.distrust.button")
    )
  }
}
