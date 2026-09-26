// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.projectView

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.ui.UISettings
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import java.lang.ref.SoftReference

private data class CachedMarkdownStructureModel(
  val file: SoftReference<PsiFile>,
  val modificationStamp: Long,
  val model: SoftReference<StructureViewModel>,
)

private val MARKDOWN_STRUCTURE_MODEL = Key.create<CachedMarkdownStructureModel>("markdown.navbar.structure.model")

class MarkdownNavBarModelExtension : StructureAwareNavBarModelExtension() {
  override val language: Language = MarkdownLanguage.INSTANCE

  override fun getLeafElement(dataProvider: DataMap): PsiElement? {
    if (!UISettings.getInstance().showMembersInNavigationBar) return null
    val psiFile = dataProvider[CommonDataKeys.PSI_FILE] ?: return null
    if (!psiFile.isValid) return null
    val markdownFile = psiFile.viewProvider.getPsi(language) ?: return null
    val editor = dataProvider[CommonDataKeys.EDITOR] ?: return null
    if (!markdownFile.isValid) return null

    return try {
      val model = getStructureViewModel(markdownFile, editor) ?: return null
      (model.currentEditorElement as? PsiElement)?.takeIf { it.isValid }?.originalElement
    }
    catch (_: IndexNotReadyException) {
      null
    }
  }

  private fun getStructureViewModel(file: PsiFile, editor: Editor): StructureViewModel? {
    val cachedModel = editor.getUserData(MARKDOWN_STRUCTURE_MODEL)
    if (cachedModel?.file?.get() == file && cachedModel.modificationStamp == file.modificationStamp) {
      cachedModel.model.get()?.let { return it }
    }

    val model = createModel(file, editor) ?: return null
    editor.putUserData(MARKDOWN_STRUCTURE_MODEL, CachedMarkdownStructureModel(SoftReference(file), file.modificationStamp, SoftReference(model)))
    return model
  }

  override fun getPresentableText(`object`: Any?): @NlsSafe String? {
    val element = `object` as? PsiElement ?: return null
    if (element.language != language) return null
    if (element is MarkdownHeader && element.name.isNullOrBlank()) return null
    return MarkdownStructureElement(element).presentableText
  }
}
