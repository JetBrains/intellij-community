// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.googleDocs

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import org.intellij.plugins.markdown.MarkdownBundle

class GoogleDocsImportAction: AnAction(MarkdownBundle.message("markdown.google.docs.import.action.name")) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    GoogleDocsImportDialog(project).show()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = Registry.`is`("markdown.google.docs.import.action.enable")
  }
}
