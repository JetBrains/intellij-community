// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.googleDocs

import com.google.api.client.auth.oauth2.Credential
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ex.ClipboardUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.importFrom.docx.MarkdownImportDocxDialog
import org.intellij.plugins.markdown.google.authorization.GoogleAuthorizationManager

class GoogleDocsImportPasteProvider : PasteProvider {
  companion object {
    private const val docsUrlPrefix = "https://docs.google.com/document/d/"
    private const val docsUrlSuffix = "/edit"

    private val docsRegEx: Regex get() = "$docsUrlPrefix(\\S*)$docsUrlSuffix".toRegex()
  }

  override fun performPaste(dataContext: DataContext) {
    val copiedLink = ClipboardUtil.getTextInClipboard()?.takeWhile { it != '#' }
    if (copiedLink == null) return

    if (docsRegEx.containsMatchIn(copiedLink.toString())) {
      val docsId = copiedLink.removePrefix(docsUrlPrefix).removeSuffix(docsUrlSuffix)
      val project = CommonDataKeys.PROJECT.getData(dataContext)!!

      val credential = GoogleAuthorizationManager(project).getCredentials() ?: return
      GoogleDocsImportTask(project, credential, docsId).queue()
    }
  }

  override fun isPastePossible(dataContext: DataContext): Boolean = true

  override fun isPasteEnabled(dataContext: DataContext): Boolean =
    Registry.`is`("markdown.google.docs.import.paste.link.enable") && LangDataKeys.IDE_VIEW.getData (dataContext) != null

  private inner class GoogleDocsImportTask(project: Project, private val credential: Credential, private val docsId: String)
    : Task.Modal(project, MarkdownBundle.message("markdown.google.load.file.progress.title"), true) {

    private var loadedFile: VirtualFile? = null

    override fun run(indicator: ProgressIndicator) {
      loadedFile = GoogleDocsFileLoader().loadFile(credential, docsId)
    }

    override fun onSuccess() {
      if (loadedFile != null) {
        val suggestedFilePath = project.basePath!!
        MarkdownImportDocxDialog(loadedFile!!, project, suggestedFilePath).show()
      }
    }
  }
}
