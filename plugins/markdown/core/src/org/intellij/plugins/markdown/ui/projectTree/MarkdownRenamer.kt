// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.projectTree

import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class MarkdownRenamer(element: MarkdownFile?, newName: String?) : AutomaticRenamer() {

  init {
    if (element != null) {
      val boundDocuments = findBoundDocument(element)
      myElements.addAll(boundDocuments)
      suggestAllNames(element.name, newName)
    }
  }

  override fun isSelectedByDefault(): Boolean = true

  override fun getDialogTitle(): String = MarkdownBundle.message("markdown.rename.dialog.title")

  override fun getDialogDescription(): String = MarkdownBundle.message("markdown.rename.dialog.description")

  override fun entityName(): String = MarkdownBundle.message("markdown.rename.entity.name")

  companion object {
    private val docExtensions = listOf("pdf", "docx", "html")

    fun findBoundDocument(element: MarkdownFile): List<PsiFile> {
      val elementDir = element.parent ?: return emptyList()
      val elementName = element.virtualFile.nameWithoutExtension

      return elementDir.children.filterIsInstance<PsiFile>().filter {
        it.virtualFile.extension?.lowercase() in docExtensions && it.virtualFile.nameWithoutExtension == elementName
      }
    }
  }
}
