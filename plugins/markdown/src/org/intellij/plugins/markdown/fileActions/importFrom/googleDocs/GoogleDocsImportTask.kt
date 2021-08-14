// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.googleDocs

import com.google.api.client.auth.oauth2.Credential
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.importFrom.docx.MarkdownImportDocxDialog
import java.io.File

internal class GoogleDocsImportTask(project: Project,
                                    private val credential: Credential,
                                    private val docsId: String,
                                    private val suggestedFilePath: String = project.basePath!!)
  : Task.Modal(project, MarkdownBundle.message("markdown.google.load.file.progress.title"), true) {

  private var loadedFile: VirtualFile? = null

  override fun run(indicator: ProgressIndicator) {
    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        loadedFile = GoogleDocsFileLoader().loadFile(credential, docsId)
      }
    }
  }

  override fun onFinished() {
    if (loadedFile != null && loadedFile!!.exists()) {
      deleteFile()
    }
  }

  override fun onSuccess() {
    if (loadedFile != null) {
      val importTaskTitle = MarkdownBundle.message("markdown.google.docs.import.task.title")
      val importDialogTitle = MarkdownBundle.message("markdown.google.docs.import.dialog.title")
      val fullFilePath = File(suggestedFilePath, loadedFile!!.name).path

      MarkdownImportDocxDialog(loadedFile!!, importTaskTitle, importDialogTitle, project, fullFilePath).show()
      deleteFile()
    }
  }

  private fun deleteFile() {
    ApplicationManager.getApplication().runWriteAction {
      loadedFile!!.delete(this)
    }
  }
}
