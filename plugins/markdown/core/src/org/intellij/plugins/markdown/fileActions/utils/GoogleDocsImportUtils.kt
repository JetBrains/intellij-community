// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.utils

import com.google.api.client.auth.oauth2.Credential
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.fileActions.importFrom.docx.MarkdownImportDocxDialog
import org.intellij.plugins.markdown.fileActions.importFrom.googleDocs.GoogleDocsFileLoader
import java.io.File

object GoogleDocsImportUtils {
  private const val docsUrlPrefix = "https://docs.google.com/document/d/"
  private const val docsUrlSuffix = "/edit"

  private val docsRegEx: Regex get() = "$docsUrlPrefix(\\S{32,})($docsUrlSuffix)?".toRegex()

  fun isLinkToDocumentCorrect(link: String): Boolean = docsRegEx.containsMatchIn(link)

  fun extractDocsId(link: String) = link.takeWhile { it != '#' }.removePrefix(docsUrlPrefix).removeSuffix(docsUrlSuffix)

  @RequiresEdt
  fun importGoogleDoc(project: Project, credential: Credential, docsId: String, suggestedFilePath: String = project.basePath!!) {
    val loadedFile = service<GoogleDocsFileLoader>().loadFileFromGoogleDisk(project, credential, docsId) ?: return

    val importTaskTitle = MarkdownBundle.message("markdown.google.docs.import.task.title")
    val importDialogTitle = MarkdownBundle.message("markdown.google.docs.import.dialog.title")
    val fullFilePath = File(suggestedFilePath, loadedFile.name).path

    MarkdownImportDocxDialog(loadedFile, importTaskTitle, importDialogTitle, project, fullFilePath).show()
  }
}
