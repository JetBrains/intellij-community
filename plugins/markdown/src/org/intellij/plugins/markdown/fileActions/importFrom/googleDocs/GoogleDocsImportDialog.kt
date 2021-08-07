// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.googleDocs

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.EditorTextField
import com.intellij.ui.layout.*
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.utils.GoogleDocsImportUtils
import org.intellij.plugins.markdown.google.authorization.GoogleAuthorizationManager
import javax.swing.JComponent

class GoogleDocsImportDialog(private val project: Project) : DialogWrapper(project, true) {

  companion object {
    private const val PREFERRED_WIDTH = 500
  }

  private val docsLinkField = EditorTextField()

  init {
    title = MarkdownBundle.message("markdown.google.docs.import.dialog.title")
    super.init()
  }

  override fun createCenterPanel(): JComponent = panel {
    row(MarkdownBundle.message("markdown.google.docs.import.dialog.url.field")) {
      cell {
        docsLinkField(growX).withValidationOnApply { validateDocsLink(it) }.focused()
      }
    }
  }.apply {
    withPreferredWidth(PREFERRED_WIDTH)
  }

  override fun doOKAction() {
    val docsLink = docsLinkField.text
    val docsId = GoogleDocsImportUtils.extractDocsId(docsLink)

    close(OK_EXIT_CODE)

    val credential = GoogleAuthorizationManager(project).getCredentials() ?: return
    GoogleDocsImportTask(project, credential, docsId).queue()
  }

  private fun ValidationInfoBuilder.validateDocsLink(field: EditorTextField): ValidationInfo? {
    val docsLink = field.text

    return when {
      field.isNull || docsLink.isEmpty() -> error(MarkdownBundle.message("markdown.google.docs.import.empty.link.error"))
      !GoogleDocsImportUtils.isLinkToDocumentCorrect(docsLink) -> error(MarkdownBundle.message("markdown.google.docs.import.invalid.url"))
      else -> null
    }
  }
}
