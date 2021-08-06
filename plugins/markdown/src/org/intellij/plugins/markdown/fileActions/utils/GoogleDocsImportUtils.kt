// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.utils

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.MarkdownNotifier
import org.intellij.plugins.markdown.fileActions.importFrom.docx.MarkdownImportDocxDialog
import org.intellij.plugins.markdown.fileActions.importFrom.googleDocs.GoogleDocsFileLoader
import org.intellij.plugins.markdown.google.authorization.GoogleCredentials
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils.createCredentialsForGoogleApi
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils.tryToReLogin
import java.io.File

object GoogleDocsImportUtils {
  private val LOG = logger<GoogleDocsImportUtils>()
  private const val docsUrlPrefix = "https://docs.google.com/document/d/"
  private const val docsUrlSuffix = "/edit"

  private val docsRegEx: Regex get() = "$docsUrlPrefix(\\S{32,})($docsUrlSuffix)?".toRegex()

  fun isLinkToDocumentCorrect(link: String): Boolean = docsRegEx.containsMatchIn(link)

  fun extractDocsId(link: String) = link.takeWhile { it != '#' }.removePrefix(docsUrlPrefix).removeSuffix(docsUrlSuffix)

  @RequiresEdt
  fun importGoogleDoc(project: Project, credential: Credential, docsId: String, suggestedFilePath: String = project.basePath!!) {
    var loadedFile: VirtualFile? = null

    try {
      loadedFile = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
        GoogleDocsFileLoader().loadFile(credential, docsId)
      }, MarkdownBundle.message("markdown.google.load.file.progress.title"), true, project)

      val importTaskTitle = MarkdownBundle.message("markdown.google.docs.import.task.title")
      val importDialogTitle = MarkdownBundle.message("markdown.google.docs.import.dialog.title")
      val fullFilePath = File(suggestedFilePath, loadedFile.name).path

      MarkdownImportDocxDialog(loadedFile, importTaskTitle, importDialogTitle, project, fullFilePath).show()
    }
    catch (e: GoogleJsonResponseException) {
      LOG.error(e.localizedMessage)
    }
    finally {
      ApplicationManager.getApplication().runWriteAction {
        if (loadedFile != null && loadedFile.exists()) {
          loadedFile.delete(this)
        }
      }
    }
  }
}
