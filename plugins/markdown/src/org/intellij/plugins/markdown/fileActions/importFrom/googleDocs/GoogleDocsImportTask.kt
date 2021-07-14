// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.googleDocs

import com.google.api.client.auth.oauth2.Credential
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.importFrom.docx.MarkdownImportDocxDialog

internal class GoogleDocsImportTask(project: Project, private val credential: Credential, private val docsId: String)
  : Task.Modal(project, MarkdownBundle.message("markdown.google.load.file.progress.title"), true) {

  private var loadedFile: VirtualFile? = null

  override fun run(indicator: ProgressIndicator) {
    loadedFile = GoogleDocsFileLoader().loadFile(credential, docsId)
  }

  override fun onSuccess() {
    if (loadedFile != null) {
      val suggestedFilePath = project.basePath!!
      val importTaskTitle =  MarkdownBundle.message("markdown.google.docs.import.task.title")

      MarkdownImportDocxDialog(loadedFile!!, importTaskTitle, project, suggestedFilePath).show()
    }
  }
}
