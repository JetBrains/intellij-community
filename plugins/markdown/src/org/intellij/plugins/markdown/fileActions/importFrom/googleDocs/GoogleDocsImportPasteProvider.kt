// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.importFrom.googleDocs

import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ex.ClipboardUtil
import com.intellij.openapi.util.registry.Registry
import org.intellij.plugins.markdown.fileActions.utils.GoogleDocsImportUtils
import org.intellij.plugins.markdown.google.authorization.GoogleAuthorizationManager

class GoogleDocsImportPasteProvider : PasteProvider {

  override fun performPaste(dataContext: DataContext) {
    val copiedLink = ClipboardUtil.getTextInClipboard()?.takeWhile { it != '#' }
    if (copiedLink == null) return

    if (GoogleDocsImportUtils.isLinkToDocumentCorrect(copiedLink.toString())) {
      val docsId = GoogleDocsImportUtils.extractDocsId(copiedLink.toString())
      val project = CommonDataKeys.PROJECT.getData(dataContext)!!

      val credential = GoogleAuthorizationManager(project).getCredentials() ?: return
      GoogleDocsImportTask(project, credential, docsId).queue()
    }
  }

  override fun isPastePossible(dataContext: DataContext): Boolean = true

  override fun isPasteEnabled(dataContext: DataContext): Boolean =
    Registry.`is`("markdown.google.docs.import.paste.link.enable") && LangDataKeys.IDE_VIEW.getData (dataContext) != null
}
