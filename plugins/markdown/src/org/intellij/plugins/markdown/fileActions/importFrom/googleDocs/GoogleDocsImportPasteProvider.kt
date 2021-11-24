// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.googleDocs

import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ex.ClipboardUtil
import com.intellij.openapi.util.registry.Registry
import org.intellij.plugins.markdown.fileActions.utils.GoogleDocsImportUtils
import org.intellij.plugins.markdown.fileActions.utils.GoogleDocsImportUtils.importGoogleDoc
import org.intellij.plugins.markdown.google.utils.GoogleAccountsUtils.chooseAccount

class GoogleDocsImportPasteProvider : PasteProvider {

  override fun performPaste(dataContext: DataContext) {
    val copiedLink = ClipboardUtil.getTextInClipboard() ?: return

    if (GoogleDocsImportUtils.isLinkToDocumentCorrect(copiedLink)) {
      val docsId = GoogleDocsImportUtils.extractDocsId(copiedLink)
      val project = CommonDataKeys.PROJECT.getData(dataContext)!!
      val suggestedPath = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)?.path ?: project.basePath!!

      val credentials = chooseAccount(project) ?: return
      importGoogleDoc(project, credentials, docsId, suggestedPath)
    }
  }

  override fun isPastePossible(dataContext: DataContext): Boolean = true

  override fun isPasteEnabled(dataContext: DataContext): Boolean =
    Registry.`is`("markdown.google.docs.import.paste.link.enable") && LangDataKeys.IDE_VIEW.getData(dataContext) != null
}
